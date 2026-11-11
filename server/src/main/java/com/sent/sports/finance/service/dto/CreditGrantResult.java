package com.sent.sports.finance.service.dto;

/**
 * 企业授信结果（中立出参）：客户、总额、可用额度、有效期止、状态。
 */
public record CreditGrantResult(long customerId, long totalCent, long availableCent, long validToMillis, String status) {
}
