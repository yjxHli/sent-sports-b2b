package com.sent.sports.refund.repository;

import com.sent.sports.refund.domain.RefundOrder;

import java.util.Optional;

/**
 * 退款单仓储端口。实现见 Phase 5（MyBatis）。
 */
public interface RefundRepository {
    void save(RefundOrder order);

    Optional<RefundOrder> findByRefundNo(String refundNo);
}
