package com.sent.sports.payment.service.dto;

/**
 * 发起支付结果：流水号、状态、金额（分）、订单号。
 */
public record CreatePaymentResult(String txnNo, String status, long amountCent, String orderNo) {
}
