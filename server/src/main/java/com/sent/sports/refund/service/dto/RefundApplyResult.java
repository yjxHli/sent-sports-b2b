package com.sent.sports.refund.service.dto;

/**
 * 申请退款结果（P3-19）：退款单号 + 订单号 + 状态 + 退款金额（分）。
 */
public record RefundApplyResult(String refundNo, String orderNo, String status, long amountCent) {
}
