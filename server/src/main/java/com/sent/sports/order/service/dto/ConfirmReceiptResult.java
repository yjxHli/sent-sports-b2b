package com.sent.sports.order.service.dto;

/**
 * 确认收货结果（P3-17）：订单号 + 主状态 + 是否已全部完成（完成才发奖励）。
 */
public record ConfirmReceiptResult(String orderNo, String status, boolean completed) {
}
