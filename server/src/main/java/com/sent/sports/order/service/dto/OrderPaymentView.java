package com.sent.sports.order.service.dto;

/**
 * 订单支付态中立视图（供 payment 等跨模块消费，不暴露 order 领域类型，符合 ArchUnit 规则 4）。
 */
public record OrderPaymentView(String orderNo, String status, String payStatus, long payableCent) {
}
