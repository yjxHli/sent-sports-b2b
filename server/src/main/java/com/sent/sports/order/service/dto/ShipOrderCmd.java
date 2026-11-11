package com.sent.sports.order.service.dto;

import java.util.List;

/**
 * 发货指令（P3-15）：承运商、物流单号、逐行发货数量。
 */
public record ShipOrderCmd(String orderNo, String carrier, String trackingNo, List<ShipLineCmd> lines) {
}
