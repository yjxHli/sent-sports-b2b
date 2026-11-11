package com.sent.sports.chat.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.auth.token.TokenService;
import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客服 WebSocket 端点处理器（P5-34/35/36，P6 收口扩展）：挂载于 {@code /ws/chat}。
 * <p>握手阶段从 URL query 读取 {@code token}，经 {@link TokenService#verify(String)} 校验：
 * {@code aud=admin} 注册到 {@link SessionBroadcaster} 的客服维度（一个客服可多端在线），
 * {@code aud=user} 注册到客户维度（一位用户可多端在线），其它 aud 直接关闭连接。
 * <p>MSG 报文走「会话校验 → 客户端消息号幂等 → 序号自增 → 落库 → 按发送方身份路由广播 → 回 ACK」，
 * 消息持久化与 HTTP 端共用 {@link ChatMessageRepository}，靠 {@code uk_msg_client} 唯一索引兜底，
 * 不引入分布式锁。落库 {@code SenderType} 按连接身份写 {@code CUSTOMER} / {@code STAFF}。
 * 路由方向：客户上行 → 广播给接待客服（无接待客服则排队广播）；客服上行 → 广播给会话所属客户。
 * ACK / READ 直接回显，坏报文回错误 ACK 且不断开连接。
 * <p>心跳：上行报文会调用 {@link SessionBroadcaster#touch(long, WebSocketSession)} /
 * {@link SessionBroadcaster#touchCustomer(long, WebSocketSession)}，
 * 后端 {@code WsHeartbeatJob} 定时清理长时间无上行的僵死连接，不依赖浏览器控制帧。
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    private static final String TOKEN_PARAM = "token";

    /** sessionId -> 认证通过的 staffId，用于消息分派与断线注销。 */
    private final Map<String, Long> staffBySession = new ConcurrentHashMap<>();

    /** sessionId -> 认证通过的 customerId，用户端经 aud=user 接入时登记。 */
    private final Map<String, Long> customerBySession = new ConcurrentHashMap<>();

    private final SessionBroadcaster broadcaster;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final IdGen idGen;
    private final Clock clock;

    public ChatWebSocketHandler(SessionBroadcaster broadcaster, TokenService tokenService,
                                ObjectMapper objectMapper, ChatSessionRepository chatSessionRepository,
                                ChatMessageRepository chatMessageRepository, IdGen idGen, Clock clock) {
        this.broadcaster = broadcaster;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.idGen = idGen;
        this.clock = clock;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = tokenFrom(session);
        if (token == null || token.isBlank()) {
            closeQuietly(session);
            return;
        }

        TokenService.Verified verified;
        try {
            verified = tokenService.verify(token);
        } catch (RuntimeException e) {
            closeQuietly(session);
            return;
        }
        long userId = verified.userId();
        if (TokenService.AUD_ADMIN.equals(verified.aud())) {
            staffBySession.put(session.getId(), userId);
            broadcaster.register(userId, session);
        } else if (TokenService.AUD_USER.equals(verified.aud())) {
            customerBySession.put(session.getId(), userId);
            broadcaster.registerCustomer(userId, session);
        } else {
            closeQuietly(session);
            return;
        }
        reply(session, WsEnvelope.queue(0L, 0L,
                json(Map.of("event", "CONNECTED", "aud", verified.aud(), "userId", userId,
                        "online", broadcaster.onlineCount()), "CONNECTED")));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long staffId = staffBySession.get(session.getId());
        Long customerId = customerBySession.get(session.getId());
        if (staffId != null) {
            broadcaster.touch(staffId, session);
        }
        if (customerId != null) {
            broadcaster.touchCustomer(customerId, session);
        }
        WsEnvelope envelope;
        try {
            envelope = WsEnvelope.parse(message.getPayload());
        } catch (BizException e) {
            reply(session, WsEnvelope.ack(0L, 0L,
                    json(Map.of("code", ErrorCode.PARAM_INVALID.getCode(), "reason", "invalid envelope"),
                            "{\"code\":40001}")));
            return;
        }
        if (WsEnvelope.MSG.equals(envelope.type())) {
            handleChatMessage(session, envelope, customerId, staffId);
        } else {
            // ACK / READ / QUEUE 一律直接回显给发送方
            reply(session, envelope);
        }
    }

    /**
     * MSG：校验会话、幂等、落库、按发送方身份路由广播、回 ACK。包内可见便于单测。
     * <p>customerId 与 staffId 互斥：用户端连接传 customerId，客服端连接传 staffId。
     * 落库 SenderType 据此写 CUSTOMER / STAFF；路由方向：
     * 客户上行 → 广播给接待客服（无接待客服则排队广播）；客服上行 → 广播给会话所属客户。
     */
    void handleChatMessage(WebSocketSession session, WsEnvelope envelope, Long customerId, Long staffId) {
        boolean fromCustomer = customerId != null;
        SenderType senderType = fromCustomer ? SenderType.CUSTOMER : SenderType.STAFF;
        long sessionId = envelope.sessionId();
        Optional<ChatSession> found = chatSessionRepository.findById(sessionId);
        if (found.isEmpty()) {
            reply(session, WsEnvelope.ack(sessionId, envelope.seq(),
                    json(Map.of("code", ErrorCode.NOT_FOUND.getCode(), "reason", "session not found"),
                            "{\"code\":40401}")));
            return;
        }
        ChatSession chatSession = found.get();
        String rawPayload = envelope.payload() == null ? "" : envelope.payload();
        JsonNode body = parsePayload(rawPayload);
        long senderId = fromCustomer ? customerId : (staffId == null ? 0L : staffId);
        String defaultClientId = "ws:" + senderId + ":" + sessionId + ":" + envelope.seq();
        String clientMsgId = textOrDefault(body, "clientMsgId", defaultClientId);
        String content = textOrDefault(body, "content", rawPayload);

        if (chatMessageRepository.findBySessionAndClientMsgId(sessionId, clientMsgId).isPresent()) {
            reply(session, WsEnvelope.ack(sessionId, envelope.seq(),
                    json(Map.of("event", "DUPLICATE", "clientMsgId", clientMsgId), "DUPLICATE")));
            return;
        }
        try {
            int seq = chatSession.appendMessage();
            long now = clock.instant().toEpochMilli();
            chatSession.touch(now);
            chatSessionRepository.save(chatSession);
            chatMessageRepository.save(new ChatMessage(idGen.next("MSG"), sessionId, senderType,
                    content, clientMsgId, seq, now));
            WsEnvelope outbound = WsEnvelope.msg(sessionId, seq, rawPayload);
            if (fromCustomer) {
                Long assignedStaffId = chatSession.getStaffId();
                if (assignedStaffId != null) {
                    broadcaster.broadcast(assignedStaffId, outbound);
                } else {
                    broadcaster.broadcastAll(WsEnvelope.queue(sessionId, seq, rawPayload));
                }
            } else {
                broadcaster.broadcastToCustomer(chatSession.getCustomerId(), outbound);
            }
            reply(session, WsEnvelope.ack(sessionId, seq,
                    json(Map.of("event", "ACCEPTED", "seq", seq, "clientMsgId", clientMsgId), "ACCEPTED")));
        } catch (DuplicateKeyException concurrentDuplicate) {
            // uk_msg_client 命中：并发下另一端已落库，按幂等成功处理
            reply(session, WsEnvelope.ack(sessionId, envelope.seq(),
                    json(Map.of("event", "DUPLICATE", "clientMsgId", clientMsgId), "DUPLICATE")));
        } catch (BizException e) {
            reply(session, WsEnvelope.ack(sessionId, envelope.seq(),
                    json(Map.of("code", e.getErrorCode().getCode(), "reason", "rejected"),
                            "{\"code\":40901}")));
        } catch (RuntimeException e) {
            log.warn("客服消息落库失败, sessionId={}, senderType={}", sessionId, senderType, e);
            reply(session, WsEnvelope.ack(sessionId, envelope.seq(),
                    json(Map.of("code", ErrorCode.SYSTEM_ERROR.getCode(), "reason", "persist failed"),
                            "{\"code\":50001}")));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long staffId = staffBySession.remove(session.getId());
        if (staffId != null) {
            broadcaster.unregister(staffId, session);
        }
        Long customerId = customerBySession.remove(session.getId());
        if (customerId != null) {
            broadcaster.unregisterCustomer(customerId, session);
        }
    }

    /** 直接向当前会话回写一帧：加锁 + isOpen 判断 + 异常只记日志。 */
    private void reply(WebSocketSession session, WsEnvelope envelope) {
        synchronized (session) {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(new TextMessage(envelope.toJson()));
            } catch (Exception e) {
                log.warn("WebSocket 回写失败, sessionId={}", session.getId(), e);
            }
        }
    }

    private JsonNode parsePayload(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            return node == null || node.isNull() ? objectMapper.createObjectNode() : node;
        } catch (Exception e) {
            return objectMapper.createObjectNode(); // 纯文本消息也接受，content 回落为原文
        }
    }

    private String textOrDefault(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? fallback : value;
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception e) {
            log.warn("关闭未认证 WebSocket 会话失败, sessionId={}", session.getId(), e);
        }
    }

    /** 从握手 URL 的 query 中取 token 参数（不依赖 Servlet API）。 */
    private String tokenFrom(WebSocketSession session) {
        URI uri = session.getUri();
        String query = (uri == null) ? null : uri.getQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0 && TOKEN_PARAM.equals(pair.substring(0, i))) {
                return pair.substring(i + 1);
            }
        }
        return null;
    }

    /** 借注入的 ObjectMapper 生成结构化控制帧 payload；序列化失败时回退到纯文本。 */
    private String json(Map<String, Object> fields, String fallback) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception e) {
            return fallback;
        }
    }
}
