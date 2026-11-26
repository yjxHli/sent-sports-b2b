package com.sent.sports.chat.service.dto;

import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.SessionType;

/**
 * 发起会话结果（中立出参）：会话号、状态、接待客服（排队为空）、是否复用已有会话、接待模式。
 * <p>{@code sessionType} 必须回给前端：机器人在接待时客户侧要能正常输入（不是「排队中」），
 * 前端靠这个字段决定输入框是否禁用。</p>
 */
public record CreateSessionResult(long sessionId, ChatSessionStatus status, Long staffId,
                                  boolean reused, SessionType sessionType) {

    /** 兼容旧调用：不带接待模式时按 HUMAN。 */
    public CreateSessionResult(long sessionId, ChatSessionStatus status, Long staffId, boolean reused) {
        this(sessionId, status, staffId, reused, SessionType.HUMAN);
    }
}
