package com.sent.sports.chat.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.auth.token.TokenService;
import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatMsgType;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.BotConversationUseCase;
import com.sent.sports.chat.service.ChatNotifier;
import com.sent.sports.chat.service.dto.BotTurnResult;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.common.util.SensitiveWordFilter;
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

    /** 敏感词拒绝帧的人话文案：前端直接弹给用户，不再二次加工。 */
    private static final String SENSITIVE_REASON = "消息包含违禁词，请文明发言";

    /** sessionId -> 认证通过的 staffId，用于消息分派与断线注销。 */
    private final Map<String, Long> staffBySession = new ConcurrentHashMap<>();

    /** sessionId -> 认证通过的 customerId，用户端经 aud=user 接入时登记。 */
    private final Map<String, Long> customerBySession = new ConcurrentHashMap<>();

    private final SessionBroadcaster broadcaster;
    private final ChatNotifier notifier;
    private final TokenService tokenService;
    private final ObjectMapper objectMapper;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final IdGen idGen;
    private final Clock clock;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final BotConversationUseCase botConversationUseCase;

    public ChatWebSocketHandler(SessionBroadcaster broadcaster, ChatNotifier notifier,
                                TokenService tokenService, ObjectMapper objectMapper,
                                ChatSessionRepository chatSessionRepository,
                                ChatMessageRepository chatMessageRepository, IdGen idGen, Clock clock,
                                SensitiveWordFilter sensitiveWordFilter,
                                BotConversationUseCase botConversationUseCase) {
        this.broadcaster = broadcaster;
        this.notifier = notifier;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.idGen = idGen;
        this.clock = clock;
        this.sensitiveWordFilter = sensitiveWordFilter;
        this.botConversationUseCase = botConversationUseCase;
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
        } else if (WsEnvelope.TYPING.equals(envelope.type())) {
            // 「正在输入」只转发不落库：客户上行 → 接待客服；客服上行 → 会话所属客户
            handleTyping(session, envelope, customerId, staffId);
        } else {
            // ACK / READ / QUEUE 一律直接回显给发送方
            reply(session, envelope);
        }
    }

    /**
     * TYPING：把「对方正在输入」提示转发给对方，不写库、不分配序号。
     * <p>路由口径与 MSG 对齐——客户上行且会话已分配才直投接待客服，
     * 否则走队列广播（让空闲坐席看到排队客户正在打字）；客服上行直达会话所属客户。
     * 会话不存在（已删/坏报文）时静默忽略：输入提示属于弱语义，不值得回错误帧打扰用户。
     */
    void handleTyping(WebSocketSession session, WsEnvelope envelope, Long customerId, Long staffId) {
        long sessionId = envelope.sessionId();
        Optional<ChatSession> found = chatSessionRepository.findById(sessionId);
        if (found.isEmpty()) {
            return;
        }
        ChatSession chatSession = found.get();
        String payload = envelope.payload() == null ? "{}" : envelope.payload();
        WsEnvelope outbound = WsEnvelope.typing(sessionId, payload);
        if (customerId != null) {
            Long assignedStaffId = chatSession.getStaffId();
            if (chatSession.getStatus() == ChatSessionStatus.ACTIVE && assignedStaffId != null) {
                broadcaster.broadcast(assignedStaffId, outbound);
            } else {
                broadcaster.broadcastAll(outbound);
            }
            return;
        }
        if (staffId != null) {
            broadcaster.broadcastToCustomer(chatSession.getCustomerId(), outbound);
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
        // 图片消息：content 是文件服务返回的 fileId，落库时 msg_type=IMAGE，渲染由前端拼 /api/files/{fileId}
        String msgType = textOrDefault(body, "msgType", "TEXT");

        if (chatMessageRepository.findBySessionAndClientMsgId(sessionId, clientMsgId).isPresent()) {
            reply(session, WsEnvelope.ack(sessionId, envelope.seq(),
                    json(Map.of("event", "DUPLICATE", "clientMsgId", clientMsgId), "DUPLICATE")));
            return;
        }
        // 敏感词风控（2026-11-19）：WS 上行与 REST 兜底必须同口径阻断——
        // 只在 REST 拦，客户端切到长连接就能绕过。命中即回错误 ACK，不落库、不广播。
        // 只检 TEXT：IMAGE 的 content 是 fileId、ORDER 是订单 JSON，匹配它们只会误杀。
        if (ChatMsgType.of(msgType) == ChatMsgType.TEXT && sensitiveWordFilter.contains(content)) {
            reply(session, WsEnvelope.ack(sessionId, envelope.seq(),
                    json(Map.of("code", ErrorCode.SENSITIVE_MESSAGE.getCode(),
                            "reason", SENSITIVE_REASON, "clientMsgId", clientMsgId),
                            "{\"code\":40003}")));
            return;
        }
        try {
            // 机器人接待（2026-11-24）：BOT 会话的文本消息先交给智能助手，
            // 命中知识库就直接回、答不上来或客户要人工就切 HUMAN 进排队，
            // 都不把这条消息投给坐席（转人工那一下除外——坐席得看到客户说了什么）。
            if (fromCustomer && chatSession.isBot() && ChatMsgType.of(msgType) == ChatMsgType.TEXT) {
                BotTurnResult bot = botConversationUseCase.handle(chatSession, content, clientMsgId);
                if (bot.handled()) {
                    if (bot.notifyStaff()) {
                        broadcaster.broadcastAll(WsEnvelope.queue(sessionId, bot.customerSeq(), rawPayload));
                    }
                    reply(session, WsEnvelope.ack(sessionId, bot.customerSeq(),
                            json(Map.of("event", "ACCEPTED", "seq", bot.customerSeq(),
                                    "clientMsgId", clientMsgId), "ACCEPTED")));
                    return;
                }
            }
            int seq = chatSession.appendMessage();
            long now = clock.instant().toEpochMilli();
            chatSession.touch(now);
            // 客服直接回复「未分配」会话时自动接管（谁回复谁接待）。
            // 不接管的话会话会一直停在 WAITING / staffId=null，客户后续每条消息都走
            // broadcastAll(QUEUE)，客服端收不到——表现为「客户发的消息客服要刷新才看得到」。
            // 会话状态不是 WAITING（已分配 / 已结束）时 assign 抛 STATE_CONFLICT，忽略即可：
            // 已分配走原有接待人路由，已结束仍按既有语义落库并由 appendMessage 拦截。
            if (!fromCustomer && takeOverIfUnassigned(chatSession, senderId)) {
                // 首次回复即接管：通知客户「已接入」，用户端排队提示随之消失
                notifier.sessionAssigned(chatSession.getCustomerId(), sessionId, senderId);
            }
            chatSessionRepository.save(chatSession);
            chatMessageRepository.save(new ChatMessage(idGen.next("MSG"), sessionId, senderType,
                    content, clientMsgId, seq, now, ChatMsgType.of(msgType)));
            WsEnvelope outbound = WsEnvelope.msg(sessionId, seq, rawPayload);
            if (fromCustomer) {
                Long assignedStaffId = chatSession.getStaffId();
                // 只有「ACTIVE 且已分配」才直投接待客服；WAITING（含释放回队列但库里残留
                // staff_id 的脏数据）一律排队广播，否则消息会投给已离线的坐席而无人收到。
                if (chatSession.getStatus() == ChatSessionStatus.ACTIVE && assignedStaffId != null) {
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
                    json(Map.of("code", e.getErrorCode().getCode(), "reason", e.resolvedMessage()),
                            "{\"code\":40901}")));
        } catch (RuntimeException e) {
            log.warn("客服消息落库失败, sessionId={}, senderType={}", sessionId, senderType, e);
            reply(session, WsEnvelope.ack(sessionId, envelope.seq(),
                    json(Map.of("code", ErrorCode.SYSTEM_ERROR.getCode(), "reason", "persist failed"),
                            "{\"code\":50001}")));
        }
    }

    /**
     * 未分配会话被客服回复时，把会话接管给该客服（WAITING → ACTIVE, staffId = sender）。
     * <p>已分配（ACTIVE）/ 已结束（CLOSED）时 {@link ChatSession#assign(long)} 抛
     * STATE_CONFLICT，此处吞掉：不改变既有接待人与既有语义，只是不做接管。
     *
     * @return 是否真的完成了接管（调用方据此决定是否下发「已接入客服」通知）
     */
    private boolean takeOverIfUnassigned(ChatSession chatSession, long staffId) {
        if (chatSession.getStaffId() != null || staffId <= 0) {
            return false;
        }
        try {
            chatSession.assign(staffId);
            return true;
        } catch (BizException ignored) {
            // 非 WAITING（已结束）无需接管
            return false;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {        Long staffId = staffBySession.remove(session.getId());
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
