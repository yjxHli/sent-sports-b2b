package com.sent.sports.refund.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.coupon.service.CouponDeduction;
import com.sent.sports.member.service.MemberRefundGateway;
import com.sent.sports.order.service.OrderRefundGateway;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.order.service.dto.OrderRefundView;
import com.sent.sports.order.service.dto.RefundLineView;
import com.sent.sports.payment.service.PaymentRefundGateway;
import com.sent.sports.refund.domain.RefundAction;
import com.sent.sports.refund.domain.RefundCalculator;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.stock.service.StockRefundGateway;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 退款回调用例（P3-23）：实现 {@link RefundCallbackService#process} 执行实际退款落地——
 * 支付流水置 REFUNDED、扣回赠送积分、退回抵扣积分(均经 member 门面)、释放库存额度(经 stock 门面)、
 * 全退关单(经 order 门面，REFUND_DONE→CLOSED)。任一步失败置 FAILED 并落库，不向外抛，保证退款单状态自洽。
 * 仅依赖各模块 service 门面接口，符合 ArchUnit 规则 4。
 *
 * <p>失败语义：支付失败、扣回积分失败、退回积分失败（钱已退但积分账不平）时置 FAILED 需人工对账；
 * 而释放额度 / 关单属下游副作用，若其失败则保留 REFUNDING 以便重试（钱已退、副作用待补）。
 */
@Service
public class RefundCallbackUseCase implements RefundCallbackService {

    private final RefundRepository refundRepository;
    private final PaymentRefundGateway paymentRefundGateway;
    private final OrderRefundViewService orderRefundViewService;
    private final MemberRefundGateway memberRefundGateway;
    private final CouponDeduction couponDeduction;
    private final StockRefundGateway stockRefundGateway;
    private final OrderRefundGateway orderRefundGateway;

    public RefundCallbackUseCase(RefundRepository refundRepository,
                                 PaymentRefundGateway paymentRefundGateway,
                                 OrderRefundViewService orderRefundViewService,
                                 MemberRefundGateway memberRefundGateway,
                                 CouponDeduction couponDeduction,
                                 StockRefundGateway stockRefundGateway,
                                 OrderRefundGateway orderRefundGateway) {
        this.refundRepository = refundRepository;
        this.paymentRefundGateway = paymentRefundGateway;
        this.orderRefundViewService = orderRefundViewService;
        this.memberRefundGateway = memberRefundGateway;
        this.couponDeduction = couponDeduction;
        this.stockRefundGateway = stockRefundGateway;
        this.orderRefundGateway = orderRefundGateway;
    }

    @UseCase
    @Override
    public void process(RefundOrder refundOrder) {
        String orderNo = refundOrder.getOrderNo();
        long amountCent = refundOrder.getAmountCent();

        // 1) 支付流水置 REFUNDED（最先，保证钱先退到用户）
        try {
            paymentRefundGateway.markRefunded(orderNo);
        } catch (BizException e) {
            refundOrder.transit(RefundAction.REFUND_FAIL);
            refundRepository.save(refundOrder);
            return;
        }

        // 2) 读取订单视图（customerId 供退积分）；缺失则置 FAILED
        Optional<OrderRefundView> viewOpt = orderRefundViewService.getView(orderNo);
        if (viewOpt.isEmpty()) {
            refundOrder.transit(RefundAction.REFUND_FAIL);
            refundRepository.save(refundOrder);
            return;
        }
        OrderRefundView view = viewOpt.get();

        // 3) 扣回积分（优先，失败则整体置 FAILED——钱已退但需人工对账）
        //    同步调用并真实写 point_log，带退款单号做幂等键：重复回调不会二次扣回
        try {
            memberRefundGateway.refundPoints(view.customerId(), refundOrder.getRefundNo(), amountCent);
        } catch (BizException e) {
            refundOrder.transit(RefundAction.REFUND_FAIL);
            refundRepository.save(refundOrder);
            return;
        }

        // 4) 退回抵扣积分（P5 闭环）。用户支付时被扣掉的抵扣积分，退款时必须还回去，
        //    否则「钱退回来了、积分被吃了」。口径：整单退全额、部分退按商品金额比例（见 RefundCalculator）。
        long goodsPaidCent = 0L;
        try {
            goodsPaidCent = view.items().stream().mapToLong(RefundLineView::paidAmountCent).sum();
            long deducted = memberRefundGateway.deductedPointsOf(view.customerId(), orderNo);
            long pointsToReturn = RefundCalculator.pointsToReturn(
                    deducted, amountCent, goodsPaidCent, view.payableCent());
            if (pointsToReturn > 0L) {
                memberRefundGateway.returnDeductedPoints(
                        view.customerId(), refundOrder.getRefundNo(), pointsToReturn);
                refundOrder.setPointRefund(pointsToReturn);
            }
        } catch (BizException e) {
            // 钱已退、积分没回：置 FAILED 交人工对账，不能当作退款成功
            refundOrder.transit(RefundAction.REFUND_FAIL);
            refundRepository.save(refundOrder);
            return;
        }

        // 4b) 整单退才返还优惠券：部分退款不退券，否则同一张券会被用两次。
        //     与积分共用 RefundCalculator.isFullRefund 口径，避免「积分退了、券没退」。
        if (RefundCalculator.isFullRefund(amountCent, goodsPaidCent, view.payableCent())) {
            try {
                couponDeduction.returnCoupon(orderNo);
            } catch (BizException e) {
                // 券没退回去属于用户权益受损，与积分同理置 FAILED 交人工对账
                refundOrder.transit(RefundAction.REFUND_FAIL);
                refundRepository.save(refundOrder);
                return;
            }
        }

        // 5) 释放库存额度（未发货部分回补待检）——下游副作用，失败保留 REFUNDING 重试
        stockRefundGateway.releaseOnRefund(orderNo);

        // 6) 全退关单（REFUND_DONE → CLOSED）——下游副作用，失败保留 REFUNDING 重试
        orderRefundGateway.closeOnFullRefund(orderNo);

        // 7) 成功：置 REFUNDED 并落库（point_refund 随本次 save 一并写入）
        refundOrder.transit(RefundAction.REFUND_SUCCESS);
        refundRepository.save(refundOrder);
    }
}
