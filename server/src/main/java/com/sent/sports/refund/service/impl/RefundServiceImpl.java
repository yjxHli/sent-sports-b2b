package com.sent.sports.refund.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.coupon.service.CouponDeduction;
import com.sent.sports.member.service.MemberRefundGateway;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.order.service.dto.OrderRefundView;
import com.sent.sports.order.service.dto.RefundLineView;
import com.sent.sports.payment.service.PaymentRefundGateway;
import com.sent.sports.refund.domain.RefundCalculator;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.refund.service.RefundService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;

/**
 * 退款门面实现：A2 迟到支付回调仍写退款仓储（DB 化后落 refund_order）；
 * 管理端关单走 {@link #createCloseRefund}，直接真库落 refund_order + 调模拟退款网关。
 */
@Service
public class RefundServiceImpl implements RefundService {

    private final RefundRepository refundRepository;
    private final RefundOrderMapper refundOrderMapper;
    private final PaymentRefundGateway paymentRefundGateway;
    private final OrderRefundViewService orderRefundViewService;
    private final MemberRefundGateway memberRefundGateway;
    private final CouponDeduction couponDeduction;
    private final IdGen idGen;
    private final Clock clock;

    public RefundServiceImpl(RefundRepository refundRepository, IdGen idGen, Clock clock,
                             RefundOrderMapper refundOrderMapper,
                             PaymentRefundGateway paymentRefundGateway,
                             OrderRefundViewService orderRefundViewService,
                             MemberRefundGateway memberRefundGateway,
                             CouponDeduction couponDeduction) {
        this.refundRepository = refundRepository;
        this.idGen = idGen;
        this.clock = clock;
        this.refundOrderMapper = refundOrderMapper;
        this.paymentRefundGateway = paymentRefundGateway;
        this.orderRefundViewService = orderRefundViewService;
        this.memberRefundGateway = memberRefundGateway;
        this.couponDeduction = couponDeduction;
    }

    @Override
    public void createOnlyRefund(String orderNo, long amountCent, String reason) {
        long orderId = orderRefundViewService.getView(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.PARAM_INVALID, "订单不存在"))
                .orderId();
        String refundNo = idGen.next("RF");
        RefundOrder ro = new RefundOrder(refundNo, orderNo, orderId, amountCent, "ONLY_REFUND", reason,
                RefundStatus.REFUNDING, clock.millis());
        refundRepository.save(ro);
    }

    @Override
    public String createCloseRefund(String orderNo, long orderId, long amountCent, String reason) {
        String refundNo = idGen.next("RF");
        // 网关先于落库：网关拒绝时抛 BIZ_REJECT，@UseCase 事务整体回滚，不留脏退款单。
        paymentRefundGateway.refund(orderNo, amountCent);

        // 管理端关单同样是「整单退款」：用户支付时被扣掉的抵扣积分必须还回去，
        // 否则关一次单就吃掉用户的积分。口径与退款回调共用 RefundCalculator，
        // 退回流水落 type=REFUND_RETURN、source_id=退款单号，重放不会二次退回。
        long pointRefund = returnDeductedPoints(orderNo, refundNo, amountCent);
        // 全额关单：用户下单时核销的券必须退回账户，否则「单取消了、券没了」。
        // 只有整单退才退券——部分退款不退券，否则半退之后还能拿同一张券再抵一次。
        couponDeduction.returnCoupon(orderNo);
        refundOrderMapper.insertCloseRefund(refundNo, orderId, amountCent, reason, pointRefund);
        return refundNo;
    }

    /** 退回该订单的抵扣积分，返回实际退回数（0 = 没用积分 / 未扣过）。 */
    private long returnDeductedPoints(String orderNo, String refundNo, long amountCent) {
        Optional<OrderRefundView> viewOpt = orderRefundViewService.getView(orderNo);
        if (viewOpt.isEmpty()) {
            return 0L; // 订单都不在：关单已在 order 侧校验过，这里不制造孤立流水
        }
        OrderRefundView view = viewOpt.get();
        long goodsPaidCent = view.items().stream().mapToLong(RefundLineView::paidAmountCent).sum();
        long deducted = memberRefundGateway.deductedPointsOf(view.customerId(), orderNo);
        long points = RefundCalculator.pointsToReturn(deducted, amountCent, goodsPaidCent, view.payableCent());
        if (points > 0L) {
            memberRefundGateway.returnDeductedPoints(view.customerId(), refundNo, points);
        }
        return points;
    }
}
