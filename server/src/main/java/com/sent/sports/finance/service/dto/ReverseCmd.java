package com.sent.sports.finance.service.dto;

/**
 * 回款冲销命令（中立入参）：付款单号 + 操作人 + 原因。
 */
public record ReverseCmd(String paymentNo, long operatorId, String reason) {
}
