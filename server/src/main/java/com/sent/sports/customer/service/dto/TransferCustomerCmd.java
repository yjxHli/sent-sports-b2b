package com.sent.sports.customer.service.dto;

/**
 * 客户改派命令（中立入参）：客户、目标归属、原因、操作人。
 */
public record TransferCustomerCmd(long customerId, long toStaffId, String reason, long operatorId) {
}
