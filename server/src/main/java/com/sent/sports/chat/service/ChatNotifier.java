package com.sent.sports.chat.service;

import com.sent.sports.chat.domain.SenderType;

/**
 * 客服会话通知端口（跨层解耦）：用例只声明「要通知谁」，具体是否走 WebSocket 由 ws 层实现。
 * <p>放在 service 包内，便于 {@code CloseSessionUseCase} / {@code AssignSessionUseCase} 等用例
 * 只依赖抽象（ArchUnit 规则 4：不直接依赖 ws 基础设施）。当前唯一实现是
 * {@code com.sent.sports.chat.ws.WsChatNotifier}，按客户维度下发 {@code ASSIGNED} 帧。</p>
 */
public interface ChatNotifier {

    /**
     * 会话已被某客服接入（人工接待 / 客服首次回复自动接管 / 结束会话后自动补位）。
     * <p>用户端据此隐藏排队提示并解锁输入框。
     *
     * @param customerId 客户号（下发的目标连接维度）
     * @param sessionId  会话号
     * @param staffId    接入的客服号；未知时可为 null
     */
    void sessionAssigned(long customerId, long sessionId, Long staffId);

    /**
     * 一条消息已落库（REST 兜底发送路径）：按会话状态路由下发。
     * <p>ACTIVE 且已分配 → 直投接待客服；否则 → 广播给全部在线坐席（排队等待接入）。
     * <p>WebSocket 直接发送的消息由 {@code ChatWebSocketHandler} 自己广播，不走这里，避免重复推送。
     */
    void messagePublished(com.sent.sports.chat.domain.ChatSession session,
                          com.sent.sports.chat.domain.ChatMessage message);

    /**
     * 会话已结束（人工关闭 / 超时自动结束）：推给客户与接待客服。
     * <p>客户据此显示「本次会话已结束」并禁用输入；客服端把对应 Tab 置为灰色「已结束」，
     * 但保留 Tab 由客服主动关闭。重复关闭不重复推送由用例层保证。</p>
     */
    void sessionClosed(long customerId, long sessionId, Long staffId, String reason);

    /**
     * 推一条提示给客户（2026-11-24）：机器人回复、人工接入提示都走这条。
     * <p>2026-11-24 二次改造：帧里带上 {@code senderType}。机器人回复要按「正常会话气泡 + 机器人头像」
     * 渲染，而「客服已接入」这类旁白要渲染成居中浅灰提示——两者都走 SYSTEM 帧，
     * 客户端只能靠发送方区分，光看 event 分不出来（都是 SYSTEM_MESSAGE）。</p>
     *
     * @param customerId 目标客户号
     * @param sessionId  会话号
     * @param seq        该提示在会话内的消息序号（已落库的提示必须带上；0 表示纯通知、未落库）
     * @param content    提示文案（纯文本；前端不再二次加工）
     * @param senderType 发送方：{@code SenderType.BOT} 渲染成气泡，{@code SenderType.SYSTEM} 渲染成居中提示
     */
    void systemMessage(long customerId, long sessionId, int seq, String content, SenderType senderType);

    /**
     * 「正在为您转接人工客服」提示（2026-11-24）。
     * <p>单独一个方法而不是复用 {@link #systemMessage}：前端要据此把会话从机器人切回人工排队口径
     * （排队条亮起、输入框按排队禁用），靠文案匹配太脆，必须带结构化的 event。</p>
     *
     * @param seq        该提示的消息序号（提示已落库，客户端据此与历史记录去重）
     * @param tip        提示文案
     * @param senderType 发送方（转接提示由机器人说出，客户侧按机器人气泡渲染）
     */
    void transferStarted(long customerId, long sessionId, int seq, String tip, SenderType senderType);
}
