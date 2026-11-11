package com.sent.sports.payment.repository.impl;

import com.sent.sports.payment.domain.PaymentTxn;
import com.sent.sports.payment.domain.PaymentTxnStatus;
import com.sent.sports.payment.mapper.PaymentTxnMapper;
import com.sent.sports.payment.mapper.po.PaymentTxnPO;
import com.sent.sports.payment.repository.PaymentRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 支付流水仓储（真库实现，验收缺口补齐）：把 {@code payment_txn} 作为唯一真源，
 * 替换原先的进程内适配器，使「发起支付 → 渠道回调」两步都真实落库。
 *
 * <p>防重只用 DB 唯一键，不引入分布式锁：
 * <ul>
 *   <li>{@code uk_txn_no}：同一渠道流水至多一行，重复通知回查到 PAID 即幂等返回；</li>
 *   <li>{@code uk_txn_idem}：同一幂等键重复发起支付时由 {@code findByIdemKey} 回查首次流水；</li>
 *   <li>状态推进一律「条件更新」（{@code WHERE status = 'CREATED'}），迟到/重复回调更新 0 行不算错误。</li>
 * </ul>
 */
@Repository
public class DbPaymentRepository implements PaymentRepository {

    private final PaymentTxnMapper mapper;

    public DbPaymentRepository(PaymentTxnMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(PaymentTxn txn) {
        PaymentTxnPO existing = mapper.selectByTxnNo(txn.getTxnNo());
        if (existing == null) {
            mapper.insert(toPO(txn));
            return;
        }
        switch (txn.getStatus()) {
            case PAID -> mapper.markCallbackPaid(txn.getTxnNo(), txn.getPayChannelTradeNo(), "PAID");
            case FAILED, CLOSED -> mapper.closeOnFailure(
                    txn.getTxnNo(), txn.getStatus().name(), txn.getStatus().name());
            // REFUNDED 不能走 closeOnFailure：那条 SQL 限定 status='CREATED'，
            // 已支付的流水永远匹配不到，会导致「订单已全额退款、支付流水却还是 PAID」。
            case REFUNDED -> mapper.markRefunded(txn.getTxnNo());
            case CREATED -> { /* 已落库的 CREATED 流水无需重复写入 */ }
        }
    }

    @Override
    public Optional<PaymentTxn> findByIdemKey(String idemKey) {
        if (idemKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectByIdemKey(idemKey)).map(this::toDomain);
    }

    @Override
    public Optional<PaymentTxn> findByTxnNo(String txnNo) {
        return Optional.ofNullable(mapper.selectByTxnNo(txnNo)).map(this::toDomain);
    }

    @Override
    public Optional<PaymentTxn> findByOrderNo(String orderNo) {
        if (orderNo == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectLatestByOrderNo(orderNo)).map(this::toDomain);
    }

    private PaymentTxnPO toPO(PaymentTxn txn) {
        PaymentTxnPO po = new PaymentTxnPO();
        po.setTxnNo(txn.getTxnNo());
        po.setOrderNo(txn.getOrderNo());
        po.setAmountCent(txn.getAmountCent());
        po.setChannel(txn.getChannel());
        po.setIdemKey(txn.getIdemKey());
        po.setStatus(txn.getStatus().name());
        po.setRequestAt(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(txn.getCreatedAtMillis()), ZoneId.systemDefault()));
        return po;
    }

    private PaymentTxn toDomain(PaymentTxnPO po) {
        long createdAt = po.getRequestAt() == null
                ? 0L
                : po.getRequestAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        PaymentTxnStatus status;
        try {
            status = PaymentTxnStatus.valueOf(po.getStatus());
        } catch (IllegalArgumentException | NullPointerException e) {
            status = PaymentTxnStatus.CREATED;
        }
        return PaymentTxn.restore(po.getTxnNo(), po.getOrderNo(),
                po.getAmountCent() == null ? 0L : po.getAmountCent(), po.getChannel(),
                po.getIdemKey(), createdAt, status, po.getChannelTradeNo());
    }
}
