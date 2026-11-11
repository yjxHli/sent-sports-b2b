package com.sent.sports.finance.service.dto;

import java.util.List;

/**
 * 核销命令（中立入参）：付款单号 + 客户 + 多条（应收 id + 金额）+ 操作人。
 */
public record WriteOffCmd(long customerId, String paymentNo, List<WriteOffLine> lines, long operatorId) {
}
