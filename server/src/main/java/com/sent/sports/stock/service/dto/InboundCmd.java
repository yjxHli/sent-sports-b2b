package com.sent.sports.stock.service.dto;

/**
 * 手工入库命令（中立入参）：SKU + 数量 + 原因 + 操作人 + 备注（spec §8.3 填数量与原因即可）。
 */
public record InboundCmd(long skuId, long qty, String reason, long operatorId, String remark) {
}
