package com.sent.sports.chat.service.dto;

import com.sent.sports.chat.domain.ChatSessionStatus;

/**
 * 发起会话结果（中立出参）：会话号、状态、接待客服（排队为空）、是否复用已有会话。
 */
public record CreateSessionResult(long sessionId, ChatSessionStatus status, Long staffId, boolean reused) {
}
