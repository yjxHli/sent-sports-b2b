package com.sent.sports.payment.repository.impl;

import com.sent.sports.payment.domain.PaymentTxn;
import com.sent.sports.payment.repository.PaymentRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 支付流水仓储（进程内适配器，已停用）。
 * <p>验收缺口补齐后由 {@link DbPaymentRepository} 提供 {@code payment_txn} 真库读写，
 * 本类不再注册为 Bean，仅保留作纯内存语义的对照实现，供不加载 Spring 的实验使用。</p>
 */
public class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<String, PaymentTxn> byTxnNo = new ConcurrentHashMap<>();

    @Override
    public void save(PaymentTxn txn) {
        byTxnNo.put(txn.getTxnNo(), txn);
    }

    @Override
    public Optional<PaymentTxn> findByIdemKey(String idemKey) {
        return byTxnNo.values().stream().filter(t -> idemKey != null && idemKey.equals(t.getIdemKey())).findFirst();
    }

    @Override
    public Optional<PaymentTxn> findByTxnNo(String txnNo) {
        return Optional.ofNullable(byTxnNo.get(txnNo));
    }

    @Override
    public Optional<PaymentTxn> findByOrderNo(String orderNo) {
        return byTxnNo.values().stream().filter(t -> orderNo != null && orderNo.equals(t.getOrderNo())).findFirst();
    }
}
