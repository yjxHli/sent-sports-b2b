package com.sent.sports.stock.service.dto;

/**
 * 录入实盘数结果（中立出参）：回执盘点单号、账面/实盘/差异数量、状态。
 */
public record RecordCheckResult(String checkNo, long bookQty, long actualQty, long diffQty, String status) {
}
