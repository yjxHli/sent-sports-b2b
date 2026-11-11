package com.sent.sports.chat.service.dto;

/**
 * 发送消息结果（中立出参）：消息号、会话内序号、会话号。
 */
public record SendMessageResult(String messageId, int seq, long sessionId) {
}
