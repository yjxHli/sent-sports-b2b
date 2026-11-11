package com.sent.sports.stock.service.dto;

/**
 * 创建盘点单命令（中立入参）：SKU + 操作人；账面数量由用例层从库存台账取当前实物量。
 */
public record CreateCheckCmd(long skuId, long operatorId) {
}
