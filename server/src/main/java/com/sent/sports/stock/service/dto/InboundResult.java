package com.sent.sports.stock.service.dto;

/**
 * 手工入库结果（中立出参）：回执 SKU、入库后实物库存与可售库存。
 */
public record InboundResult(long skuId, long afterPhysicalQty, long afterAvailableQty) {
}
