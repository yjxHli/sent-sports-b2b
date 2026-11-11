package com.sent.sports.chat.service.dto;

/**
 * 关闭会话命令（中立入参）：会话号、关闭原因(枚举名 DONE/TIMEOUT/LEFT/TRANSFERRED)、操作人。
 */
public record CloseCmd(long sessionId, String reason, long operatorId) {
}
