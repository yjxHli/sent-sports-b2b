package com.sent.sports.chat.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.service.ChatNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link ChatNotifier} 的 WebSocket 实现：按客户维度下发 {@code ASSIGNED} 帧。
 * <p>用户端收到后隐藏排队提示、解锁输入框；客户不在线时静默丢弃（下次拉取队列状态接口也能自愈）。
 */
@Component
public class WsChatNotifier implements ChatNotifier {

    private static final Logger log = LoggerFactory.getLogger(WsChatNotifier.class);

    private final SessionBroadcaster broadcaster;
    private final ObjectMapper objectMapper;

    public WsChatNotifier(SessionBroadcaster broadcaster, ObjectMapper objectMapper) {
        this.broadcaster = broadcaster;
        this.objectMapper = objectMapper;
    }

    @Override
    public void sessionAssigned(long customerId, long sessionId, Long staffId) {
        Map<String, Object> body = new HashMap<>();
        body.put("event", "ASSIGNED");
        body.put("sessionId", sessionId);
        body.put("staffId", staffId);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.warn("接入通知序列化失败, sessionId={}", sessionId, e);
            payload = "{\"event\":\"ASSIGNED\",\"sessionId\":" + sessionId + "}";
        }
        broadcaster.broadcastToCustomer(customerId, WsEnvelope.assigned(sessionId, payload));
    }

    @Override
    public void sessionClosed(long customerId, long sessionId, Long staffId, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("event", "SESSION_CLOSED");
        body.put("sessionId", sessionId);
        body.put("reason", reason);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.warn("会话结束通知序列化失败, sessionId={}", sessionId, e);
            payload = "{\"event\":\"SESSION_CLOSED\"}";
        }
        WsEnvelope frame = WsEnvelope.closed(sessionId, payload);
        broadcaster.broadcastToCustomer(customerId, frame);
        if (staffId != null) {
            broadcaster.broadcast(staffId, frame);
        }
    }

    @Override
    public void messagePublished(ChatSession session, ChatMessage message) {
        Map<String, Object> body = new HashMap<>();
        body.put("clientMsgId", message.getClientMsgId());
        body.put("content", message.getContent());
        body.put("senderType", message.getSenderType() == null ? null : message.getSenderType().name());
        body.put("msgType", message.getMsgType().name());
        body.put("seq", message.getSeq());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.warn("消息推送序列化失败, sessionId={}", session.getId(), e);
            payload = "{\"content\":\"\"}";
        }
        boolean assigned = session.getStatus() == ChatSessionStatus.ACTIVE && session.getStaffId() != null;
        if (assigned) {
            broadcaster.broadcast(session.getStaffId(), WsEnvelope.msg(session.getId(), message.getSeq(), payload));
        } else {
            broadcaster.broadcastAll(WsEnvelope.queue(session.getId(), message.getSeq(), payload));
        }
        // 客服 / 系统发出的消息必须直达客户本人：评价卡（SYSTEM + SATISFY）走的就是这条路径，
        // 以前只推客服，客户端的评价卡要等下次拉历史才出现（表现为「点了发送评价，客户那边没内容」）。
        // 客户自己发的消息不回流，避免同一句话在客户端出现两次。
        if (message.getSenderType() != SenderType.CUSTOMER) {
            broadcaster.broadcastToCustomer(session.getCustomerId(),
                    WsEnvelope.msg(session.getId(), message.getSeq(), payload));
        }
    }
}
