package com.sent.sports.stock.service.dto;

/**
 * 录入实盘数命令（中立入参）：盘点单号 + 实盘数量 + 操作人。
 */
public record RecordCheckCmd(String checkNo, long actualQty, long operatorId) {
}
