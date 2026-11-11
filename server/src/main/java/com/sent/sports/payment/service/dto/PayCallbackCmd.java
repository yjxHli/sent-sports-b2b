package com.sent.sports.payment.service.dto;

/**
 * 支付回调指令：流水号、订单号、支付金额（分）、渠道、渠道交易号、支付完成时间。
 * 验签（plan §7.3 步骤1）由接口层完成，此处假定已验签。
 */
public record PayCallbackCmd(String txnNo, String orderNo, long amountCent, String channel,
                            String payChannelTradeNo, long paidAtMillis) {
}
