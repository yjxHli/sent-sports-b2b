package com.sent.sports.chat.service.dto;

import com.sent.sports.chat.domain.ChatSessionStatus;

/**
 * 关闭会话结果（中立出参）：会话号、状态(应为 CLOSED)、被自动补位的排队会话号（无则 null）。
 */
public record CloseSessionResult(long sessionId, ChatSessionStatus status, Long promotedSessionId) {
}
