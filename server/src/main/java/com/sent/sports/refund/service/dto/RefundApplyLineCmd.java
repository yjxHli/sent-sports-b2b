package com.sent.sports.refund.service.dto;

/**
 * 申请退款单行指令：SKU + 申请退款金额（分）。
 */
public record RefundApplyLineCmd(long skuId, long amountCent) {
}
