package com.sent.sports.stock.service.dto;

/**
 * 创建盘点单结果（中立出参）：回执盘点单号、SKU、账面数量、状态。
 */
public record CreateCheckResult(String checkNo, long skuId, long bookQty, String status) {
}
