package com.sent.sports.chat.domain;

/**
 * 客服在线状态（plan §7.8 / spec §6.10）：在线 / 忙碌 / 离线。
 */
public enum PresenceStatus {
    ONLINE,
    BUSY,
    OFFLINE
}
