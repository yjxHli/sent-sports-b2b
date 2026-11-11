package com.sent.sports.order.service.dto;

/**
 * 订单支付态中立视图（供 payment 等跨模块消费，不暴露 order 领域类型，符合 ArchUnit 规则 4）。
 * payable=true 表示订单存在、未支付且未进入终态，可发起支付。
 */
public record OrderPaymentStatusDto(String orderNo, boolean payable, long payableCent) {
}
