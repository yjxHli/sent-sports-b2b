package com.sent.sports.finance.service.dto;

/**
 * 核销明细行（中立入参）：应收 id + 分配金额（分）。
 */
public record WriteOffLine(long receivableId, long amount) {
}
