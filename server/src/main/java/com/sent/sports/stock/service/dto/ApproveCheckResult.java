package com.sent.sports.stock.service.dto;

/**
 * 审批盘点结果（中立出参）：回执盘点单号、状态、调账后实物库存。
 */
public record ApproveCheckResult(String checkNo, String status, long afterPhysicalQty) {
}
