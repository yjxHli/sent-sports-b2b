package com.sent.sports.order.service.dto;

/**
 * 退款项视图（中立 DTO）：SKU + 行实付金额（分）+ 已退金额（分）。供 refund 模块消费，不暴露 order 领域类型。
 */
public record RefundLineView(long skuId, long paidAmountCent, long alreadyRefundedCent) {
}
