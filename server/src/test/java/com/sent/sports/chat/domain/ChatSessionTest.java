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

    /* ---------------- 接待模式（2026-11-24 机器人辅助接待） ---------------- */

    @Test
    void defaultSessionTypeIsHuman() {
        // 既有调用方（API 直接建会话、历史数据回填）一律人工，绝不能被机器人悄悄接管
        assertEquals(SessionType.HUMAN, new ChatSession(1L, 100L).getSessionType());
        assertFalse(new ChatSession(1L, 100L).isBot());
    }

    @Test
    void botSessionStaysWaitingAndTakesNoStaff() {
        ChatSession s = new ChatSession(1L, 100L, false, ChatSessionStatus.WAITING, null, 0L, 0L, 0,
                SessionType.BOT);
        assertTrue(s.isBot());
        assertEquals(ChatSessionStatus.WAITING, s.getStatus());
        assertEquals(null, s.getStaffId());
    }

    @Test
    void transferToHumanKeepsWaitingForQueue() {
        ChatSession s = new ChatSession(1L, 100L, false, ChatSessionStatus.WAITING, null, 0L, 0L, 0,
                SessionType.BOT);
        s.transferToHuman();
        assertEquals(SessionType.HUMAN, s.getSessionType());
        assertEquals(ChatSessionStatus.WAITING, s.getStatus());
    }

    @Test
    void transferToHumanOnActiveSessionKeepsActive() {
        // 人工已接管的会话不需要再排一次队；重复「转人工」也不该把它退回 WAITING
        ChatSession s = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 9001L);
        s.transferToHuman();
        assertEquals(SessionType.HUMAN, s.getSessionType());
        assertEquals(ChatSessionStatus.ACTIVE, s.getStatus());
    }

    @Test
    void transferToHumanOnClosedSessionThrows() {
        ChatSession s = new ChatSession(1L, 100L);
        s.close("done");
        BizException ex = assertThrows(BizException.class, s::transferToHuman);
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
    }

    @Test
    void assignSwitchesBotSessionToHuman() {
        // 人工接线后必须切 HUMAN：否则坐席接过来的会话仍按机器人规则拦截客户消息
        ChatSession s = new ChatSession(1L, 100L, false, ChatSessionStatus.WAITING, null, 0L, 0L, 0,
                SessionType.BOT);
        s.assign(9001L);
        assertEquals(SessionType.HUMAN, s.getSessionType());
        assertEquals(ChatSessionStatus.ACTIVE, s.getStatus());
    }
}
