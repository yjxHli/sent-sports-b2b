package com.sent.sports.finance.repository;

import com.sent.sports.finance.domain.WriteOff;

import java.util.List;

/**
 * 核销记录仓储端口（P3-38/41）：持久化核销留痕，按付款单反查（冲销解绑用）。
 * 实现见 Phase 5。
 */
public interface WriteOffRepository {
    void save(WriteOff writeOff);

    /** 反查某付款单的全部核销记录（支持分次核销后的整体冲销）。 */
    List<WriteOff> findByPaymentNo(String paymentNo);
}
