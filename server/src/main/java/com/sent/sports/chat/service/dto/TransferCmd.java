package com.sent.sports.chat.service.dto;

/**
 * 转接命令（中立入参）：会话号、目标客服、转接原因、操作人。
 * 原因必填（非空），历史消息保留不删除。
 */
public record TransferCmd(long sessionId, long toStaffId, String reason, long operatorId) {
}
