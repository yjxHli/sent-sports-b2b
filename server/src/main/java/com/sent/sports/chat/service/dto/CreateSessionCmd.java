package com.sent.sports.chat.service.dto;

import com.sent.sports.chat.domain.SessionType;

/**
 * 发起会话命令（中立入参）：客户、是否 VIP、操作人、接待模式。
 * <p>VIP 由上游以标志位传入，chat 模块不反向依赖 member/customer。</p>
 *
 * @param sessionType BOT 机器人先接待 / HUMAN 人工（省略按 HUMAN，保持既有接口口径）
 */
public record CreateSessionCmd(long customerId, boolean vip, long operatorId, SessionType sessionType) {

    /** 兼容旧调用：不指定接待模式时一律人工，避免历史调用方被机器人接管。 */
    public CreateSessionCmd(long customerId, boolean vip, long operatorId) {
        this(customerId, vip, operatorId, SessionType.HUMAN);
    }
}
