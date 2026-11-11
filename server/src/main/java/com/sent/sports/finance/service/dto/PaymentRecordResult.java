package com.sent.sports.finance.service.dto;

/**
 * 登记回款结果（中立出参）：回执付款单号、客户、金额、流水号、状态、创建时间、备注。
 */
public record PaymentRecordResult(String paymentNo, long customerId, long amountCent,
                                  String serialNo, String status, long createdAtMillis, String remark) {
}
