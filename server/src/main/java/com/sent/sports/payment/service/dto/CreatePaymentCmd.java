package com.sent.sports.payment.service.dto;

/**
 * 发起支付指令：订单号、支付金额（分）、渠道、幂等键。
 */
public record CreatePaymentCmd(String orderNo, long amountCent, String channel, String idemKey) {
}
