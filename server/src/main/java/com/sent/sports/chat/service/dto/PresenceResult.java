package com.sent.sports.chat.service.dto;

import com.sent.sports.chat.domain.PresenceStatus;

/**
 * 设置在线状态结果（中立出参）：客服、状态、本次释放回排队的会话数。
 */
public record PresenceResult(long staffId, PresenceStatus presence, int releasedSessions) {
}
