package com.sent.sports.chat.ws;

import org.springframework.web.socket.WebSocketSession;

/**
 * 客服会话广播端口（P5-36）：以客服 staffId 为维度登记在线 WebSocket 会话并下发信封。
 * <p>同一 staffId 允许多个端同时在线（PC + 移动端），用 {@link #unregister(long, WebSocketSession)}
 * 精确注销其中一端，避免新连接上线时把旧连接「静默踢掉却仍留在表里」。
 * 当前仅提供进程内实现 {@link InMemorySessionBroadcaster}；后续可替换为 Redis Pub/Sub 等跨实例实现。
 */
public interface SessionBroadcaster {

    /** 登记某客服的在线会话（同 staffId 多端并存）。 */
    void register(long staffId, WebSocketSession session);

    /** 注销某客服的全部在线会话（强制下线）。 */
    void unregister(long staffId);

    /** 精确注销某客服的某一个端（断线时调用，不影响其它端）。 */
    void unregister(long staffId, WebSocketSession session);

    /** 记录某客服的一次上行活跃（心跳/业务报文均调用），供 {@link #closeIdle(long)} 判定。 */
    void touch(long staffId, WebSocketSession session);

    /** 记录某客户的一次上行活跃（心跳/业务报文均调用），供 {@link #closeIdle(long)} 判定。 */
    void touchCustomer(long customerId, WebSocketSession session);

    /** 登记某客户的在线会话（同一客户多端并存）。 */
    void registerCustomer(long customerId, WebSocketSession session);

    /** 精确注销某客户的某一个端（断线时调用，不影响其它端）。 */
    void unregisterCustomer(long customerId, WebSocketSession session);

    /** 向指定客服的所有在线端下发一条信封；无会话 / 会话已关闭时静默跳过。 */
    void broadcast(long staffId, WsEnvelope envelope);

    /** 向指定客户的所有在线端下发一条信封；无会话 / 会话已关闭时静默跳过。 */
    void broadcastToCustomer(long customerId, WsEnvelope envelope);

    /** 向全部在线端下发（如新会话排队通知 QUEUE）。 */
    void broadcastAll(WsEnvelope envelope);

    /** 关闭并清理超过 idleTimeoutMillis 没有上行流量的会话，返回清理数量。 */
    int closeIdle(long idleTimeoutMillis);

    /** 当前在线会话（端）总数。 */
    int onlineCount();
}
