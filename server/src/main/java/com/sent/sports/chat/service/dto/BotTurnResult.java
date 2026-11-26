package com.sent.sports.chat.service.dto;

/**
 * 机器人一轮对话的落库结果（2026-11-24）。
 *
 * @param handled        机器人是否接管了这条消息（false = 人工会话，按原链路投递）
 * @param notifyStaff    这条客户消息是否要让排队坐席看到（转人工 / 答不上来时）
 * @param customerMsgId  客户消息的消息号
 * @param customerSeq    客户消息的会话内序号
 * @param replySeq       机器人回复的序号；没有回复时为 0
 */
public record BotTurnResult(boolean handled, boolean notifyStaff, String customerMsgId,
                            int customerSeq, int replySeq) {

    /** 未接管：调用方继续走原发送链路。 */
    public static BotTurnResult notHandled() {
        return new BotTurnResult(false, false, null, 0, 0);
    }
}
