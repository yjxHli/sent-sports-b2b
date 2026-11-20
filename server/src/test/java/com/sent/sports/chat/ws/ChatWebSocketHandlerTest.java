package com.sent.sports.chat.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.auth.token.TokenService;
import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.ChatNotifier;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.common.util.SensitiveWordFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 客服 WS 消息链路单测（P5-34/35/36）：落库、幂等、路由、错误帧。 */
@ExtendWith(MockitoExtension.class)
class ChatWebSocketHandlerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneId.of("UTC"));

    @Mock
    private SessionBroadcaster broadcaster;
    @Mock
    private TokenService tokenService;
    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private ChatNotifier notifier;
    @Mock
    private WebSocketSession wsSession;

    private ChatWebSocketHandler handler() {
        return new ChatWebSocketHandler(broadcaster, notifier, tokenService, new ObjectMapper(),
                chatSessionRepository, chatMessageRepository, idGen, FIXED_CLOCK,
                new SensitiveWordFilter(List.of("测试违禁词", "傻X", "加V")));
    }

    @Test
    void customerMsgPersistsAndRoutesToAssignedStaff() throws Exception {
        ChatSession session = new ChatSession(10L, 100L, ChatSessionStatus.ACTIVE, 7L);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionAndClientMsgId(10L, "c-1")).thenReturn(Optional.empty());
        when(idGen.next("MSG")).thenReturn("MSG-1");
        when(wsSession.isOpen()).thenReturn(true);

        handler().handleChatMessage(wsSession,
                WsEnvelope.msg(10L, 0L, "{\"clientMsgId\":\"c-1\",\"content\":\"你好\"}"), 100L, null);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        ChatMessage saved = captor.getValue();
        assertEquals("MSG-1", saved.getMessageId());
        assertEquals(10L, saved.getSessionId());
        assertEquals("c-1", saved.getClientMsgId());
        assertEquals("你好", saved.getContent());
        assertEquals(1, saved.getSeq());
        assertEquals(1_700_000_000_000L, saved.getCreatedAtMillis());
        assertEquals(SenderType.CUSTOMER, saved.getSenderType());
        verify(chatSessionRepository).save(session);
        verify(broadcaster).broadcast(eq(7L), any(WsEnvelope.class));
        verify(wsSession).sendMessage(any(TextMessage.class)); // ACK
    }

    /**
     * 敏感词风控（2026-11-19）：WS 上行命中后只回错误 ACK，不落库、不广播。
     * <p>REST 与 WS 必须同口径：只拦 REST 的话，客户端切到长连接就能绕过风控发脏话。</p>
     */
    @Test
    void sensitiveContentRejectedWithErrorAckAndNoPersist() throws Exception {
        ChatSession session = new ChatSession(10L, 100L, ChatSessionStatus.ACTIVE, 7L);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionAndClientMsgId(eq(10L), any())).thenReturn(Optional.empty());
        when(wsSession.isOpen()).thenReturn(true);

        handler().handleChatMessage(wsSession,
                WsEnvelope.msg(10L, 0L, "{\"clientMsgId\":\"c-bad\",\"content\":\"加V详聊\"}"), 100L, null);

        verify(chatMessageRepository, never()).save(any());
        verify(broadcaster, never()).broadcast(anyLong(), any());
        verify(broadcaster, never()).broadcastAll(any());
        ArgumentCaptor<TextMessage> frame = ArgumentCaptor.forClass(TextMessage.class);
        verify(wsSession).sendMessage(frame.capture());
        String payload = frame.getValue().getPayload();
        assertTrue(payload.contains("40003"), "错误码必须是 40003，前端据此保留输入框内容");
        assertTrue(payload.contains("违禁词"), "文案要能直接弹给用户看");
    }

    @Test
    void duplicateClientMsgIdSkipsPersist() throws Exception {        ChatSession session = new ChatSession(10L, 100L, ChatSessionStatus.ACTIVE, 7L);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionAndClientMsgId(10L, "c-1"))
                .thenReturn(Optional.of(new ChatMessage("M0", 10L,
                        com.sent.sports.chat.domain.SenderType.STAFF, "你好", "c-1", 1, 0L)));
        when(wsSession.isOpen()).thenReturn(true);

        handler().handleChatMessage(wsSession,
                WsEnvelope.msg(10L, 0L, "{\"clientMsgId\":\"c-1\",\"content\":\"你好\"}"), null, 7L);

        verify(chatMessageRepository, never()).save(any());
        verify(broadcaster, never()).broadcast(eq(7L), any());
        verify(wsSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void concurrentDuplicateKeyIsTreatedAsDuplicate() throws Exception {
        ChatSession session = new ChatSession(10L, 100L, ChatSessionStatus.ACTIVE, 7L);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionAndClientMsgId(eq(10L), any())).thenReturn(Optional.empty());
        when(idGen.next("MSG")).thenReturn("MSG-1");
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk_msg_client"))
                .when(chatMessageRepository).save(any());
        when(wsSession.isOpen()).thenReturn(true);

        handler().handleChatMessage(wsSession,
                WsEnvelope.msg(10L, 0L, "{\"clientMsgId\":\"c-1\",\"content\":\"你好\"}"), null, 7L);

        verify(wsSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void unknownSessionRepliesErrorAndSkipsPersist() throws Exception {
        when(chatSessionRepository.findById(99L)).thenReturn(Optional.empty());
        when(wsSession.isOpen()).thenReturn(true);

        handler().handleChatMessage(wsSession, WsEnvelope.msg(99L, 1L, "hello"), null, 7L);

        verify(chatMessageRepository, never()).save(any());
        verify(wsSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void staffMsgToWaitingSessionRoutesToCustomer() throws Exception {
        ChatSession session = new ChatSession(10L, 100L);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionAndClientMsgId(eq(10L), any())).thenReturn(Optional.empty());
        when(idGen.next("MSG")).thenReturn("MSG-2");
        when(wsSession.isOpen()).thenReturn(true);

        handler().handleChatMessage(wsSession, WsEnvelope.msg(10L, 0L, "纯文本消息"), null, 7L);

        verify(broadcaster).broadcastToCustomer(eq(100L), any(WsEnvelope.class));
        verify(broadcaster, never()).broadcastAll(any());
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        assertEquals(SenderType.STAFF, captor.getValue().getSenderType());
    }

    @Test
    void closedSessionIsNotPersisted() throws Exception {
        ChatSession session = new ChatSession(10L, 100L, ChatSessionStatus.CLOSED, 7L);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionAndClientMsgId(eq(10L), any())).thenReturn(Optional.empty());
        when(wsSession.isOpen()).thenReturn(true);

        handler().handleChatMessage(wsSession, WsEnvelope.msg(10L, 0L, "hello"), null, 7L);

        verify(chatMessageRepository, never()).save(any());
        verify(wsSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void staffMsgRoutesToCustomer() throws Exception {
        ChatSession session = new ChatSession(10L, 100L, ChatSessionStatus.ACTIVE, 7L);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionAndClientMsgId(eq(10L), any())).thenReturn(Optional.empty());
        when(idGen.next("MSG")).thenReturn("MSG-8");
        when(wsSession.isOpen()).thenReturn(true);

        handler().handleChatMessage(wsSession,
                WsEnvelope.msg(10L, 0L, "{\"clientMsgId\":\"c-3\",\"content\":\"已为您处理\"}"), null, 7L);

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        assertEquals(SenderType.STAFF, captor.getValue().getSenderType());
        verify(broadcaster).broadcastToCustomer(eq(100L), any(WsEnvelope.class));
        verify(broadcaster, never()).broadcast(anyLong(), any());
        verify(wsSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void waitingCustomerMsgRoutesToQueueBroadcast() throws Exception {
        ChatSession session = new ChatSession(10L, 100L);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionAndClientMsgId(eq(10L), any())).thenReturn(Optional.empty());
        when(idGen.next("MSG")).thenReturn("MSG-7");
        when(wsSession.isOpen()).thenReturn(true);

        handler().handleChatMessage(wsSession,
                WsEnvelope.msg(10L, 0L, "{\"clientMsgId\":\"c-4\",\"content\":\"在吗\"}"), 100L, null);

        verify(broadcaster).broadcastAll(any(WsEnvelope.class));
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository).save(captor.capture());
        assertEquals(SenderType.CUSTOMER, captor.getValue().getSenderType());
    }

    /**
     * 回归：客服直接回复一条「未分配（WAITING）」会话时，必须由该客服自动接管。
     * <p>历史缺陷：客服回复虽然能落库并送达客户，但会话一直停在 WAITING / staffId=null，
     * 于是客户后续每条消息都走 {@code broadcastAll(QUEUE)}，客服端收不到——表现为「客户发的消息
     * 客服要刷新才看得到」。修复后第一条客服回复即完成分配（谁回复谁接待）。
     */
    @Test
    void staffReplyToWaitingSessionTakesOverAssignment() throws Exception {
        ChatSession session = new ChatSession(10L, 100L);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionAndClientMsgId(eq(10L), any())).thenReturn(Optional.empty());
        when(idGen.next("MSG")).thenReturn("MSG-9");
        when(wsSession.isOpen()).thenReturn(true);

        handler().handleChatMessage(wsSession,
                WsEnvelope.msg(10L, 0L, "{\"clientMsgId\":\"c-5\",\"content\":\"在的\"}"), null, 7L);

        assertEquals(7L, session.getStaffId(), "客服回复未分配会话后应由该客服接管");
        assertEquals(ChatSessionStatus.ACTIVE, session.getStatus(), "接管后会话应转为 ACTIVE");
        verify(chatSessionRepository).save(session);
        verify(broadcaster).broadcastToCustomer(eq(100L), any(WsEnvelope.class));
        verify(notifier).sessionAssigned(100L, 10L, 7L);
    }

    /** 回归：接管后的会话里客户再发消息，必须直达该客服（MSG，而不是 QUEUE 广播）。 */
    @Test
    void customerMsgAfterTakeoverRoutesToAcceptingStaff() throws Exception {
        ChatSession session = new ChatSession(10L, 100L);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionAndClientMsgId(eq(10L), any())).thenReturn(Optional.empty());
        when(idGen.next("MSG")).thenReturn("MSG-9", "MSG-10");
        when(wsSession.isOpen()).thenReturn(true);

        // 1) 客服回复 → 接管为 ACTIVE/7
        handler().handleChatMessage(wsSession,
                WsEnvelope.msg(10L, 0L, "{\"clientMsgId\":\"c-5\",\"content\":\"在的\"}"), null, 7L);
        // 2) 客户接着发消息 → 应直达 7
        handler().handleChatMessage(wsSession,
                WsEnvelope.msg(10L, 0L, "{\"clientMsgId\":\"c-6\",\"content\":\"我要印校徽\"}"), 100L, null);

        verify(broadcaster).broadcast(eq(7L), any(WsEnvelope.class));
        verify(broadcaster, never()).broadcastAll(any());
    }

    /**
     * 回归：会话被释放回队列但库里还残留 staffId（历史脏数据）时，
     * 客户消息必须按 WAITING 走 QUEUE 广播，而不是投给那个（可能已离线的）坐席。
     */
    @Test
    void waitingSessionWithStaleStaffStillBroadcastsToQueue() throws Exception {
        ChatSession session = ChatSession.rehydrate(10L, 100L, false,
                ChatSessionStatus.WAITING, 7L, null, 0L, 0L, 4);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(chatMessageRepository.findBySessionAndClientMsgId(eq(10L), any())).thenReturn(Optional.empty());
        when(idGen.next("MSG")).thenReturn("MSG-11");
        when(wsSession.isOpen()).thenReturn(true);

        handler().handleChatMessage(wsSession,
                WsEnvelope.msg(10L, 0L, "{\"clientMsgId\":\"c-7\",\"content\":\"在吗\"}"), 100L, null);

        verify(broadcaster).broadcastAll(any(WsEnvelope.class));
        verify(broadcaster, never()).broadcast(anyLong(), any());
    }

    @Test
    void customerConnectionRegistersToBroadcaster() throws Exception {
        when(wsSession.getUri()).thenReturn(new URI("ws://localhost/ws/chat?token=user-token"));
        when(wsSession.getId()).thenReturn("c-session-1");
        when(wsSession.isOpen()).thenReturn(true);
        when(tokenService.verify("user-token"))
                .thenReturn(new TokenService.Verified(TokenService.AUD_USER, 123L, 1L, "jti-1", 9_999_999_999_999L));

        handler().afterConnectionEstablished(wsSession);

        verify(broadcaster).registerCustomer(eq(123L), eq(wsSession));
        verify(wsSession).sendMessage(any(TextMessage.class));
    }

    @Test
    void nonUserNonAdminConnectionIsClosed() throws Exception {
        when(wsSession.getUri()).thenReturn(new URI("ws://localhost/ws/chat?token=bad-token"));
        when(tokenService.verify("bad-token"))
                .thenReturn(new TokenService.Verified("partner", 1L, 1L, "jti", 9_999_999_999_999L));

        handler().afterConnectionEstablished(wsSession);

        verify(broadcaster, never()).register(anyLong(), any());
        verify(broadcaster, never()).registerCustomer(anyLong(), any());
        verify(wsSession).close(any());
    }

    // ------------------------------------------------------------------
    // 「正在输入」：TYPING 帧只在两端之间转发，绝不允许落库（不进 chat_message）
    // ------------------------------------------------------------------

    /** 客户打字 → 直达接待客服，且不产生任何消息记录。 */
    @Test
    void customerTypingRoutesToAssignedStaffWithoutPersisting() throws Exception {
        ChatSession session = new ChatSession(10L, 100L, ChatSessionStatus.ACTIVE, 7L);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        handler().handleTyping(wsSession, WsEnvelope.typing(10L, "{\"typing\":true}"), 100L, null);

        verify(broadcaster).broadcast(eq(7L), any(WsEnvelope.class));
        verify(chatMessageRepository, never()).save(any());
        verify(chatSessionRepository, never()).save(any());
    }

    /** 客服打字 → 直达会话所属客户。 */
    @Test
    void staffTypingRoutesToCustomerWithoutPersisting() throws Exception {
        ChatSession session = new ChatSession(10L, 100L, ChatSessionStatus.ACTIVE, 7L);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        handler().handleTyping(wsSession, WsEnvelope.typing(10L, "{\"typing\":true}"), null, 7L);

        verify(broadcaster).broadcastToCustomer(eq(100L), any(WsEnvelope.class));
        verify(chatMessageRepository, never()).save(any());
    }

    /** 会话还没分配（排队中）时客户打字：走队列广播，让任一空闲坐席看到「客户在输入」。 */
    @Test
    void waitingCustomerTypingBroadcastsToQueue() throws Exception {
        ChatSession session = new ChatSession(10L, 100L);
        when(chatSessionRepository.findById(10L)).thenReturn(Optional.of(session));

        handler().handleTyping(wsSession, WsEnvelope.typing(10L, "{\"typing\":true}"), 100L, null);

        verify(broadcaster).broadcastAll(any(WsEnvelope.class));
        verify(broadcaster, never()).broadcast(anyLong(), any());
        verify(chatMessageRepository, never()).save(any());
    }

    /** 会话已结束/不存在：静默忽略，不回错误帧、不打扰任何一方。 */
    @Test
    void typingOnUnknownSessionIsIgnored() throws Exception {
        when(chatSessionRepository.findById(404L)).thenReturn(Optional.empty());

        handler().handleTyping(wsSession, WsEnvelope.typing(404L, "{\"typing\":true}"), 100L, null);

        verify(broadcaster, never()).broadcast(anyLong(), any());
        verify(broadcaster, never()).broadcastToCustomer(anyLong(), any());
        verify(broadcaster, never()).broadcastAll(any());
    }
}
