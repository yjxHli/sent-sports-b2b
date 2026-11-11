package com.sent.sports.chat.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.auth.token.TokenService;
import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.common.util.IdGen;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private WebSocketSession wsSession;

    private ChatWebSocketHandler handler() {
        return new ChatWebSocketHandler(broadcaster, tokenService, new ObjectMapper(),
                chatSessionRepository, chatMessageRepository, idGen, FIXED_CLOCK);
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

    @Test
    void duplicateClientMsgIdSkipsPersist() throws Exception {
        ChatSession session = new ChatSession(10L, 100L, ChatSessionStatus.ACTIVE, 7L);
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
}
