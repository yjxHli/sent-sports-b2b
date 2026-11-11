package com.sent.sports.chat.service.dto;

import com.sent.sports.chat.domain.PresenceStatus;

/**
 * 设置在线状态命令（中立入参）：客服、目标状态(ONLINE/BUSY/OFFLINE)、操作人。
 */
public record SetPresenceCmd(long staffId, PresenceStatus presence, long operatorId) {
}
