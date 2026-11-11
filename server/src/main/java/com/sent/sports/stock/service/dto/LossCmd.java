package com.sent.sports.stock.service.dto;

/**
 * 报损命令（中立入参）：SKU + 数量 + 原因 + 操作人 + 备注（spec §8.3 须填数量与原因）。
 */
public record LossCmd(long skuId, long qty, String reason, long operatorId, String remark) {
}
