package com.sent.sports.chat.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.SenderType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 推送实现单测：消息路由（ACTIVE 直投接待客服 / WAITING 广播排队）与接入通知。
 */
class WsChatNotifierTest {

    private final SessionBroadcaster broadcaster = mock(SessionBroadcaster.class);
    private final WsChatNotifier notifier = new WsChatNotifier(broadcaster, new ObjectMapper());

    @Test
    void publishedMessageRoutesToAssignedStaff() {
        ChatSession active = new ChatSession(7L, 9002L, ChatSessionStatus.ACTIVE, 9001L);
        ChatMessage message = new ChatMessage("MSG1", 7L, SenderType.CUSTOMER, "在吗", "c-1", 3, 0L);

        notifier.messagePublished(active, message);

        ArgumentCaptor<WsEnvelope> captor = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(broadcaster).broadcast(eq(9001L), captor.capture());
        verify(broadcaster, never()).broadcastAll(any());
        assertEquals(WsEnvelope.MSG, captor.getValue().type());
        assertEquals(7L, captor.getValue().sessionId());
        assertEquals(3L, captor.getValue().seq());
        assertTrue(captor.getValue().payload().contains("在吗"));
        assertTrue(captor.getValue().payload().contains("c-1"));
    }

    @Test
    void publishedMessageOnWaitingSessionBroadcastsToAll() {
        ChatSession waiting = new ChatSession(7L, 9002L, ChatSessionStatus.WAITING, null);
        ChatMessage message = new ChatMessage("MSG2", 7L, SenderType.CUSTOMER, "有人吗", "c-2", 4, 0L);

        notifier.messagePublished(waiting, message);

        ArgumentCaptor<WsEnvelope> captor = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(broadcaster).broadcastAll(captor.capture());
        verify(broadcaster, never()).broadcast(anyLong(), any());
        assertEquals(WsEnvelope.QUEUE, captor.getValue().type());
        assertTrue(captor.getValue().payload().contains("有人吗"));
    }

    @Test
    void assignedNotificationGoesToCustomer() {
        notifier.sessionAssigned(9002L, 7L, 9001L);

        ArgumentCaptor<WsEnvelope> captor = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(broadcaster).broadcastToCustomer(eq(9002L), captor.capture());
        assertEquals(WsEnvelope.ASSIGNED, captor.getValue().type());
        assertEquals(7L, captor.getValue().sessionId());
        assertTrue(captor.getValue().payload().contains("ASSIGNED"));
        assertTrue(captor.getValue().payload().contains("9001"));
    }
}
