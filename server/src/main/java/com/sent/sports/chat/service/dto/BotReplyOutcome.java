package com.sent.sports.chat.service.dto;

/**
 * 机器人对一条客户消息的处理决定（2026-11-24）。
 *
 * @param action 机器人动作
 * @param reply  要回给客户的话；{@link BotAction#HANDOFF} 时为 null（静默转人工，不额外说话）
 */
public record BotReplyOutcome(BotAction action, String reply) {

    /** 机器人对这条消息的处理方式。 */
    public enum BotAction {
        /** 非机器人会话（人工接待中）：按原链路直接投递。 */
        NONE,

        /** 命中知识库：机器人回复，留在 BOT，不打扰坐席。 */
        REPLY,

        /** 客户明确要求「转人工」：切 HUMAN 进排队，并提示「正在为您转接…」。 */
        TRANSFER,

        /**
         * 机器人答不上来：切 HUMAN 进排队，但不额外说话。
         * <p>为什么静默：客户看到的就是「排队中 → 客服接入」这条老链路，
         * 不会被两句系统提示夹在中间；也避免与「已接入」提示叠在一起。</p>
         */
        HANDOFF
    }

    public static BotReplyOutcome none() {
        return new BotReplyOutcome(BotAction.NONE, null);
    }

    public boolean handled() {
        return action != BotAction.NONE;
    }

    /** 是否要交给人工（TRANSFER / HANDOFF 都会把会话切 HUMAN 并进排队）。 */
    public boolean handoff() {
        return action == BotAction.TRANSFER || action == BotAction.HANDOFF;
    }
}
