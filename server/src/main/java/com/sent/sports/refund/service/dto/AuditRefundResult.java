package com.sent.sports.refund.service.dto;

/**
 * 退款审核结果（P3-21）：退款单号 + 处理后状态。
 */
public record AuditRefundResult(String refundNo, String status) {
}
