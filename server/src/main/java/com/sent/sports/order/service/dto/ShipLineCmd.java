package com.sent.sports.order.service.dto;

/**
 * 发货单行指令：SKU + 本次发货数量。
 */
public record ShipLineCmd(long skuId, int qty) {
}
