package com.sent.sports.chat.domain;

/**
 * 客服会话状态（plan §7.8 / spec §6.10）：待接入 / 进行中 / 已结束。
 */
public enum ChatSessionStatus {
    WAITING,
    ACTIVE,
    CLOSED
}
