package com.sent.sports.order.event;

/**
 * 发货事件（P3-15）：订单发货完成，携带承运商与物流单号，供物流追踪 / 通知模块订阅。
 */
public record ShipEvent(String orderNo, String carrier, String trackingNo, long shippedAtMillis) {
}
