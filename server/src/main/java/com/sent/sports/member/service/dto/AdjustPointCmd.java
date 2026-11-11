package com.sent.sports.member.service.dto;

/**
 * 积分调整命令（中立入参）：客户、增减额（可负）、原因、业务类型、业务单号、操作人。
 */
public record AdjustPointCmd(long customerId, long delta, String reason, String bizType, String bizNo, long operatorId) {
}
