package com.sent.sports.refund.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.payment.service.PaymentRefundGateway;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.refund.service.RefundService;
import org.springframework.stereotype.Service;

import java.time.Clock;

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
    private final IdGen idGen;
    private final Clock clock;

    public RefundServiceImpl(RefundRepository refundRepository, IdGen idGen, Clock clock,
                             RefundOrderMapper refundOrderMapper,
                             PaymentRefundGateway paymentRefundGateway,
                             OrderRefundViewService orderRefundViewService) {
        this.refundRepository = refundRepository;
        this.idGen = idGen;
        this.clock = clock;
        this.refundOrderMapper = refundOrderMapper;
        this.paymentRefundGateway = paymentRefundGateway;
        this.orderRefundViewService = orderRefundViewService;
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
        refundOrderMapper.insertCloseRefund(refundNo, orderId, amountCent, reason);
        return refundNo;
    }
}
