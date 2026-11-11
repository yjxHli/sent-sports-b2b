package com.sent.sports.stock.service.dto;

/**
 * 审批盘点命令（中立入参）：盘点单号 + 操作人。确认令牌由 P4 接口层校验后注入，用例层据此调账。
 */
public record ApproveCheckCmd(String checkNo, long operatorId) {
}
