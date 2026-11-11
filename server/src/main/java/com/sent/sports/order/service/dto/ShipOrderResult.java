package com.sent.sports.order.service.dto;

/**
 * 发货结果（P3-15）：订单号 + 重算后的主状态。
 */
public record ShipOrderResult(String orderNo, String status) {
}
