package com.sent.sports.payment.repository;

import com.sent.sports.payment.domain.PaymentTxn;

import java.util.Optional;

/**
 * 支付流水仓储端口。实现见 Phase 5（MyBatis）。
 */
public interface PaymentRepository {
    void save(PaymentTxn txn);

    Optional<PaymentTxn> findByIdemKey(String idemKey);

    Optional<PaymentTxn> findByTxnNo(String txnNo);

    Optional<PaymentTxn> findByOrderNo(String orderNo);
}
