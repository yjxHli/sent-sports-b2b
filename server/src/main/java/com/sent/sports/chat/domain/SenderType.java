package com.sent.sports.chat.domain;

/**
 * 消息发送方（plan §7.8 / spec §6.10）：客户 / 客服 / 系统 / 机器人。
 *
 * <p>{@link #BOT} 与 {@link #SYSTEM} 的区别（2026-11-24 用户要求「智能客服的回答按正常会话格式发送」）：
 * 机器人回复是一句「有人格的话」，客户端要渲染成带机器人头像的普通气泡；
 * SYSTEM 则是「会话已结束」「客服已接入」这类不属于任何人的旁白，渲染成居中浅灰提示。
 * 两者混用时机器人回复只能显示成灰字旁白，客户认不出是谁在回答。</p>
 */
public enum SenderType {
    CUSTOMER,
    STAFF,
    SYSTEM,
    BOT
}
