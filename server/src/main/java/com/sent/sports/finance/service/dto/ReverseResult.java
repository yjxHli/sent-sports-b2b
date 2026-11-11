package com.sent.sports.finance.service.dto;

/**
 * 回款冲销结果（中立出参）：付款单号 + 状态。
 */
public record ReverseResult(String paymentNo, String status) {
}
