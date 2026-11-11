package com.sent.sports.chat.service.dto;

/**
 * 发起会话命令（中立入参）：客户、是否 VIP、操作人。
 * VIP 由上游以标志位传入，chat 模块不反向依赖 member/customer。
 */
public record CreateSessionCmd(long customerId, boolean vip, long operatorId) {
}
