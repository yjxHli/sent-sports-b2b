package com.sent.sports.customer.service.dto;

/**
 * 客户改派结果（中立出参）：回执客户、原归属、新归属。
 */
public record TransferCustomerResult(long customerId, long fromStaffId, long toStaffId) {
}
