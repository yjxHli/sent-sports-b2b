package com.sent.sports.chat.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatMsgType;
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

    /**
     * 评价卡（SYSTEM + SATISFY）必须同时到客服端与客户本人。
     * <p>历史缺陷：已分配会话只 {@code broadcast(staffId)}，客户端的评价卡永远收不到，
     * 表现为「客服点了发送评价，客户那边没任何内容，没法打分」。</p>
     */
    @Test
    void satisfactionCardReachesStaffAndCustomer() {
        ChatSession active = new ChatSession(7L, 9002L, ChatSessionStatus.ACTIVE, 9001L);
        ChatMessage card = new ChatMessage("MSG3", 7L, SenderType.SYSTEM, "{\"status\":\"PENDING\"}",
                "SATISFY-7", 5, 0L, ChatMsgType.SATISFY);

        notifier.messagePublished(active, card);

        ArgumentCaptor<WsEnvelope> staffFrame = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(broadcaster).broadcast(eq(9001L), staffFrame.capture());
        assertTrue(staffFrame.getValue().payload().contains("SATISFY"));

        ArgumentCaptor<WsEnvelope> customerFrame = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(broadcaster).broadcastToCustomer(eq(9002L), customerFrame.capture());
        assertEquals(WsEnvelope.MSG, customerFrame.getValue().type());
        assertEquals(7L, customerFrame.getValue().sessionId());
        assertEquals(5L, customerFrame.getValue().seq());
        assertTrue(customerFrame.getValue().payload().contains("SATISFY"),
                "客户端必须能识别出这是评价卡");
        assertTrue(customerFrame.getValue().payload().contains("SATISFY-7"),
                "评价卡 clientMsgId 要带给客户端，前端据此去重");
    }

    /**
     * 会话结束帧必须同时到客户和接待客服：客户据此显示「本次会话已结束」并禁用输入，
     * 客服端据此把 Tab 置灰为「已结束」（但保留 Tab 由客服主动关闭）。
     */
    @Test
    void closedFrameReachesBothCustomerAndAssignedStaff() {
        notifier.sessionClosed(9002L, 7L, 9001L, "DONE");

        ArgumentCaptor<WsEnvelope> customer = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(broadcaster).broadcastToCustomer(eq(9002L), customer.capture());
        assertEquals(WsEnvelope.CLOSED, customer.getValue().type());
        assertEquals(7L, customer.getValue().sessionId());
        assertTrue(customer.getValue().payload().contains("SESSION_CLOSED"));
        assertTrue(customer.getValue().payload().contains("DONE"));

        ArgumentCaptor<WsEnvelope> staff = ArgumentCaptor.forClass(WsEnvelope.class);
        verify(broadcaster).broadcast(eq(9001L), staff.capture());
        assertEquals(WsEnvelope.CLOSED, staff.getValue().type());
    }

    /** 排队中的会话没有接待客服，不能向 staffId=null 广播。 */
    @Test
    void closedFrameWithoutStaffOnlyGoesToCustomer() {
        notifier.sessionClosed(9002L, 7L, null, "TIMEOUT");

        verify(broadcaster).broadcastToCustomer(eq(9002L), any());
        verify(broadcaster, never()).broadcast(anyLong(), any());
    }

    /** 客户自己发的消息不回流给客户本人，否则同一句话在客户端出现两次 */
    @Test
    void customerMessageIsNotEchoedBackToCustomer() {
        ChatSession active = new ChatSession(7L, 9002L, ChatSessionStatus.ACTIVE, 9001L);
        ChatMessage message = new ChatMessage("MSG4", 7L, SenderType.CUSTOMER, "在吗", "c-4", 6, 0L);

        notifier.messagePublished(active, message);

        verify(broadcaster, never()).broadcastToCustomer(anyLong(), any());
    }
}
