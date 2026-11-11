package com.sent.sports.refund.service.dto;

/**
 * 退款审核指令（P3-21）：退款单号 + 动作（APPROVE / REJECT / APPEAL）+ 驳回原因。
 */
public record AuditRefundCmd(String refundNo, String action, String reason) {
}
