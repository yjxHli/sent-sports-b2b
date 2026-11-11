package com.sent.sports.finance.repository;

import com.sent.sports.finance.domain.PaymentRecord;

import java.util.Optional;

/**
 * 回款单仓储端口（P3-36/37）：登记与查询回款，流水号唯一性校验。
 * 实现见 Phase 5；用例层据此在冲突时抛 BIZ_REJECT。
 */
public interface PaymentRecordRepository {
    void save(PaymentRecord record);

    Optional<PaymentRecord> findByPaymentNo(String paymentNo);

    /** 流水号是否已存在（唯一性）。 */
    boolean existsBySerialNo(String serialNo);
}
