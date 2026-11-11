package com.sent.sports.order.service.dto;

/**
 * 确认收货单行指令：SKU + 本次确认收货数量。
 */
public record ReceiveLineCmd(long skuId, int qty) {
}
