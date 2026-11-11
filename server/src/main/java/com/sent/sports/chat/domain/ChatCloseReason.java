package com.sent.sports.chat.domain;

/**
 * 会话结束原因（plan §7.8 / spec §6.10）：完成 / 超时 / 用户离开 / 转接。
 * 作为固定枚举集合约束 {@code close} 入参；非法值由用例层映射为 PARAM_INVALID。
 */
public enum ChatCloseReason {
    DONE,
    TIMEOUT,
    LEFT,
    TRANSFERRED
}
