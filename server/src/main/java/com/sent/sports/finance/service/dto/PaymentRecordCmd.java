package com.sent.sports.finance.service.dto;

/**
 * 登记回款命令（中立入参）：客户 + 金额（分）+ 流水号 + 操作人 + 备注。
 */
public record PaymentRecordCmd(long customerId, long amountCent, String serialNo, long operatorId, String remark) {
}
