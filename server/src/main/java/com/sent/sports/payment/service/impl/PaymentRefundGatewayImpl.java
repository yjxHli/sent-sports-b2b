package com.sent.sports.payment.service.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.payment.domain.PaymentTxn;
import com.sent.sports.payment.gateway.PayGateway;
import com.sent.sports.payment.repository.PaymentRepository;
import com.sent.sports.payment.service.PaymentRefundGateway;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 支付退款门面实现：按订单号定位支付流水并置 REFUNDED，随后落库。
 * 仅依赖 payment 模块内部（仓储端口 / 领域 / 通道端口），符合 ArchUnit 规则 4。
 */
@Service
public class PaymentRefundGatewayImpl implements PaymentRefundGateway {

    private final PaymentRepository paymentRepository;
    private final PayGateway payGateway;

    public PaymentRefundGatewayImpl(PaymentRepository paymentRepository, PayGateway payGateway) {
        this.paymentRepository = paymentRepository;
        this.payGateway = payGateway;
    }

    @Override
    public void markRefunded(String orderNo) {
        PaymentTxn txn = paymentRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BizException(ErrorCode.STATE_CONFLICT, "支付流水不存在"));
        txn.markRefunded();
        paymentRepository.save(txn);
    }

    @Override
    public void refund(String orderNo, long amountCent) {
        Optional<PaymentTxn> existing = paymentRepository.findByOrderNo(orderNo);
        // 无通道流水（余额 / 授信支付）时用确定性占位号，保证网关仍能受理且不落虚假流水。
        String txnNo = existing.map(PaymentTxn::getTxnNo).orElse("RF-" + orderNo);
        if (!payGateway.refund(txnNo, orderNo, amountCent)) {
            throw new BizException(ErrorCode.BIZ_REJECT, "模拟退款网关拒绝退款");
        }
        if (existing.isPresent()) {
            PaymentTxn txn = existing.get();
            txn.markRefunded();
            paymentRepository.save(txn);
        }
    }
}
