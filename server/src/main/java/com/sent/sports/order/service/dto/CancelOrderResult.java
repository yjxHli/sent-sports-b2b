package com.sent.sports.order.service.dto;

/**
 * 取消订单结果：订单号与取消后状态（应为 CANCELLED）。
 */
public record CancelOrderResult(String orderNo, String status) {
}
