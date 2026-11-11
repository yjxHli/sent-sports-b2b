package com.sent.sports.chat.service.dto;

import com.sent.sports.chat.domain.ChatSessionStatus;

/**
 * 关闭会话结果（中立出参）：会话号、状态(应为 CLOSED)。
 */
public record CloseSessionResult(long sessionId, ChatSessionStatus status) {
}
