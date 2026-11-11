package com.sent.sports.order.repository;

import com.sent.sports.order.domain.Order;

import java.util.Optional;

/**
 * 订单仓储端口：落库并承载幂等键唯一约束，按订单号读取聚合。实现见 Phase 5（MyBatis）。
 */
public interface OrderRepository {
    void save(Order order);

    Optional<Order> findByOrderNo(String orderNo);
}
