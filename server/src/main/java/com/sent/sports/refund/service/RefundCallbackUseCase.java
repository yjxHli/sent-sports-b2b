package com.sent.sports.refund.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.member.service.MemberRefundGateway;
import com.sent.sports.order.service.OrderRefundGateway;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.order.service.dto.OrderRefundView;
import com.sent.sports.payment.service.PaymentRefundGateway;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.stock.service.StockRefundGateway;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 退款回调用例（P3-23）：实现 {@link RefundCallbackService#process} 执行实际退款落地——
 * 支付流水置 REFUNDED、退积分优先(经 member 门面)、释放库存额度(经 stock 门面)、
 * 全退关单(经 order 门面，REFUND_DONE→CLOSED)。任一步失败置 FAILED 并落库，不向外抛，保证退款单状态自洽。
 * 仅依赖各模块 service 门面接口，符合 ArchUnit 规则 4。
 *
 * <p>失败语义：支付或退积分失败（钱未完整退到用户）时置 FAILED 需人工对账；
 * 而释放额度 / 关单属下游副作用，若其失败则保留 REFUNDING 以便重试（钱已退、副作用待补）。
 */
@Service
public class RefundCallbackUseCase implements RefundCallbackService {

    private final RefundRepository refundRepository;
    private final PaymentRefundGateway paymentRefundGateway;
    private final OrderRefundViewService orderRefundViewService;
    private final MemberRefundGateway memberRefundGateway;
    private final StockRefundGateway stockRefundGateway;
    private final OrderRefundGateway orderRefundGateway;

    public RefundCallbackUseCase(RefundRepository refundRepository,
                                 PaymentRefundGateway paymentRefundGateway,
                                 OrderRefundViewService orderRefundViewService,
                                 MemberRefundGateway memberRefundGateway,
                                 StockRefundGateway stockRefundGateway,
                                 OrderRefundGateway orderRefundGateway) {
        this.refundRepository = refundRepository;
        this.paymentRefundGateway = paymentRefundGateway;
        this.orderRefundViewService = orderRefundViewService;
        this.memberRefundGateway = memberRefundGateway;
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
            refundOrder.setStatus(RefundStatus.FAILED);
            refundRepository.save(refundOrder);
            return;
        }

        // 2) 读取订单视图（customerId 供退积分）；缺失则置 FAILED
        Optional<OrderRefundView> viewOpt = orderRefundViewService.getView(orderNo);
        if (viewOpt.isEmpty()) {
            refundOrder.setStatus(RefundStatus.FAILED);
            refundRepository.save(refundOrder);
            return;
        }
        OrderRefundView view = viewOpt.get();

        // 3) 退积分（优先，失败则整体置 FAILED——钱已退但需人工对账）
        try {
            memberRefundGateway.refundPoints(view.customerId(), amountCent);
        } catch (BizException e) {
            refundOrder.setStatus(RefundStatus.FAILED);
            refundRepository.save(refundOrder);
            return;
        }

        // 4) 释放库存额度（未发货部分回补待检）——下游副作用，失败保留 REFUNDING 重试
        stockRefundGateway.releaseOnRefund(orderNo);

        // 5) 全退关单（REFUND_DONE → CLOSED）——下游副作用，失败保留 REFUNDING 重试
        orderRefundGateway.closeOnFullRefund(orderNo);

        // 6) 成功：置 REFUNDED 并落库
        refundOrder.setStatus(RefundStatus.REFUNDED);
        refundRepository.save(refundOrder);
    }
}
