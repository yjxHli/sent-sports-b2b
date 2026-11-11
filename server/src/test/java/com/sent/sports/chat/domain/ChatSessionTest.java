package com.sent.sports.chat.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-41 会话状态测试（TDD 红）：plan §7.8 / spec §6.10。
 * 待接入 / 进行中 / 已结束（WAITING / ACTIVE / CLOSED）；
 * 同一客户仅一个进行中。
 */
class ChatSessionTest {

    @Test
    void newSessionIsWaiting() {
        ChatSession s = new ChatSession(1L, 100L);
        assertEquals(ChatSessionStatus.WAITING, s.getStatus());
        assertFalse(s.isActive());
    }

    @Test
    void assignToActive() {
        ChatSession s = new ChatSession(1L, 100L);
        s.assign(5L);
        assertEquals(ChatSessionStatus.ACTIVE, s.getStatus());
        assertEquals(5L, s.getStaffId());
        assertTrue(s.isActive());
    }

    @Test
    void assignTwiceThrows() {
        ChatSession s = new ChatSession(1L, 100L);
        s.assign(5L);
        BizException ex = assertThrows(BizException.class, () -> s.assign(6L));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
    }

    @Test
    void closeActive() {
        ChatSession s = new ChatSession(1L, 100L);
        s.assign(5L);
        s.close("done");
        assertEquals(ChatSessionStatus.CLOSED, s.getStatus());
    }

    @Test
    void closeWaitingAllowed() {
        ChatSession s = new ChatSession(1L, 100L);
        s.close("left");
        assertEquals(ChatSessionStatus.CLOSED, s.getStatus());
    }

    @Test
    void closeTwiceThrows() {
        ChatSession s = new ChatSession(1L, 100L);
        s.close("x");
        BizException ex = assertThrows(BizException.class, () -> s.close("y"));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
    }

    @Test
    void singleActiveSessionPerCustomer() {
        ChatSession a = new ChatSession(1L, 100L);
        ChatSession b = new ChatSession(2L, 100L);
        a.assign(5L);
        assertTrue(ChatSession.hasActiveSession(List.of(a, b), 100L));
        assertFalse(ChatSession.canStartNew(List.of(a, b), 100L));
        a.close("done");
        assertFalse(ChatSession.hasActiveSession(List.of(a, b), 100L));
        assertTrue(ChatSession.canStartNew(List.of(a, b), 100L));
    }
}
