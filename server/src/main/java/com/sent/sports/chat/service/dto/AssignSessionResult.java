package com.sent.sports.chat.service.dto;

import com.sent.sports.chat.domain.ChatSessionStatus;

/**
 * 分配 / 转接结果（中立出参）：会话号、状态、接待客服。
 */
public record AssignSessionResult(long sessionId, ChatSessionStatus status, Long staffId) {
}
