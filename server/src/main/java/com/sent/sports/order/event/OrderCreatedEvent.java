package com.sent.sports.order.event;

/**
 * 订单创建事件（P3-02）。事件类型之一，供库存/积分/通知等订阅。
 */
public record OrderCreatedEvent(String orderNo, long customerId, long payableCent, long createdAt) {
}
