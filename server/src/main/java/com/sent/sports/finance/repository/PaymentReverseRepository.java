package com.sent.sports.finance.repository;

import com.sent.sports.finance.domain.PaymentReverse;

/**
 * 回款冲销留痕仓储端口（P3-40/41）：持久化冲销轨迹。
 * 实现见 Phase 5。
 */
public interface PaymentReverseRepository {
    void save(PaymentReverse reverse);
}
