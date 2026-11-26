package com.sent.sports.chat.domain;

/**
 * 客服会话的接待模式（2026-11-24，对应 {@code chat_session.session_type}）。
 *
 * <p>BOT：客户进线先由「智能助手小森」接待，命中知识库直接答，不占用坐席；
 * HUMAN：人工接待。客户发「转人工」或机器人答不上来时由 BOT 切到 HUMAN，
 * 会话留在 WAITING 排队，工作台按原口径接线。</p>
 *
 * <p>默认 HUMAN：历史会话与 API 直接建的会话都不该被机器人接管，
 * 否则既有人工客服链路的用例会突然变成「发了消息没人看到」。</p>
 */
public enum SessionType {

    /** 机器人接待。 */
    BOT,

    /** 人工接待。 */
    HUMAN;

    /** 容错解析：脏数据 / 旧数据一律按 HUMAN 处理。 */
    public static SessionType of(String value) {
        if (value == null || value.isBlank()) {
            return HUMAN;
        }
        for (SessionType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return HUMAN;
    }
}
