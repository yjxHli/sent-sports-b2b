package com.sent.sports.finance.service.dto;

/**
 * 企业授信命令（中立入参）：客户 + 总额（分）+ 账期天数 + 有效期 + 操作人 + 备注。
 */
public record CreditGrantCmd(long customerId, long totalCent, long accountPeriodDays,
                             long validFromMillis, long validToMillis, long operatorId, String remark) {
}
