package com.sent.sports.stock.service.dto;

/**
 * 报损结果（中立出参）：回执 SKU、报损后实物库存与可售库存。
 */
public record LossResult(long skuId, long afterPhysicalQty, long afterAvailableQty) {
}
