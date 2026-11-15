package com.sent.sports.chat.service;

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
}
