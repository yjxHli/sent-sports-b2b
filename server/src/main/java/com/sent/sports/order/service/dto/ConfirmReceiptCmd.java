package com.sent.sports.order.service.dto;

import java.util.List;

/**
 * 确认收货指令（P3-17）：逐包裹确认，逐行收货数量。
 */
public record ConfirmReceiptCmd(String orderNo, List<ReceiveLineCmd> lines) {
}
