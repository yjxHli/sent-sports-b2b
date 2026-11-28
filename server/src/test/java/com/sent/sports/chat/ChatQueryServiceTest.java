package com.sent.sports.chat;

import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.dto.ChatDtos;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.impl.ChatQueryServiceImpl;
import com.sent.sports.common.error.BizException;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会话查询实现测试：历史消息按 afterSeq 增量返回（此前为恒空桩，会把前端气泡刷没）。
 */
class ChatQueryServiceTest extends BaseUseCaseTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatSessionRepository sessionRepository;

    @InjectMocks
    private ChatQueryServiceImpl queryService;

    @Test
    void historyReturnsAllMessagesWhenAfterSeqZero() {
        when(chatMessageRepository.findBySessionId(33L)).thenReturn(List.of(
                new ChatMessage("MSG1", 33L, SenderType.CUSTOMER, "这个球能印我们校徽吗？", "c1", 1, 1000L),
                new ChatMessage("MSG2", 33L, SenderType.STAFF, "可以的，请在订单详情上传图稿", "c2", 2, 2000L)));

        List<ChatDtos.MessageVO> rows = queryService.history(33L, 0);

        assertEquals(2, rows.size());
        assertEquals("MSG1", rows.get(0).messageId());
        assertEquals("CUSTOMER", rows.get(0).senderType());
        assertEquals("STAFF", rows.get(1).senderType());
        assertEquals(2, rows.get(1).seq());
    }

    @Test
    void historySkipsMessagesAtOrBeforeAfterSeq() {        when(chatMessageRepository.findBySessionId(33L)).thenReturn(List.of(
                new ChatMessage("MSG1", 33L, SenderType.CUSTOMER, "a", "c1", 1, 1000L),
                new ChatMessage("MSG2", 33L, SenderType.STAFF, "b", "c2", 2, 2000L)));

        List<ChatDtos.MessageVO> rows = queryService.history(33L, 1);

        assertEquals(1, rows.size());
        assertEquals("MSG2", rows.get(0).messageId());
    }

    @Test
    void historyEmptyForUnknownSession() {
        when(chatMessageRepository.findBySessionId(404L)).thenReturn(List.of());

        assertTrue(queryService.history(404L, 0).isEmpty());
    }

    /** 排队位置 = 排在该客户之前的 WAITING 数量 + 1。 */
    @Test
    void queuePositionCountsWaitingAhead() {
        ChatSession waiting = new ChatSession(7L, 9002L, false, ChatSessionStatus.WAITING, null, 5_000L, 5_000L, 0);
        when(sessionRepository.findById(7L)).thenReturn(Optional.of(waiting));
        when(sessionRepository.countWaitingBefore(7L, 5_000L)).thenReturn(2);

        ChatDtos.QueueVO vo = queryService.queue(7L);

        assertEquals("WAITING", vo.status());
        assertEquals(3, vo.position(), "前方 2 人 → 我排第 3");
    }

    /** 已分配的会话不再排队，位置为 0，并带回接待客服便于用户端提示「已接入」。 */
    @Test
    void queuePositionIsZeroWhenAssigned() {
        ChatSession active = new ChatSession(8L, 9002L, ChatSessionStatus.ACTIVE, 9001L);
        when(sessionRepository.findById(8L)).thenReturn(Optional.of(active));

        ChatDtos.QueueVO vo = queryService.queue(8L);

        assertEquals("ACTIVE", vo.status());
        assertEquals(0, vo.position());
        assertEquals(9001L, vo.staffId());
    }

    @Test
    void queueUnknownSessionRejected() {
        when(sessionRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(BizException.class, () -> queryService.queue(404L));
    }

    /**
     * 用户要求：客户进入聊天页不能算「咨询客服」，只有发出首条消息才建会话。
     * <p>因此「当前会话」接口只能查，绝不能顺手 create/save；否则工作台会冒出一条没有任何客户消息的幽灵进线。</p>
     */
    @Test
    void currentOpenOnlyReadsAndNeverCreatesSession() {
        when(sessionRepository.findByCustomerId(9002L)).thenReturn(java.util.List.of());

        ChatDtos.SessionVO vo = queryService.currentOpen(9002L);

        assertEquals(null, vo, "没有会话时返回 null，由前端在首次发消息时才调创建接口");
        verify(sessionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listSessionsReturnsCustomerHistoryNewestFirstAndHonorsLimit() {
        ChatSession older = new ChatSession(10L, 9002L, false, ChatSessionStatus.CLOSED, 9001L,
                1_000L, 2_000L, 3);
        ChatSession newer = new ChatSession(9L, 9002L, false, ChatSessionStatus.ACTIVE, 9003L,
                3_000L, 4_000L, 5);
        // 仓储契约：selectByCustomerId 按 id DESC 返回，最新会话在前
        when(sessionRepository.findByCustomerId(9002L)).thenReturn(List.of(newer, older));

        List<ChatDtos.SessionSummaryVO> rows = queryService.listSessions(9002L, 1);

        assertEquals(1, rows.size(), "limit 必须下推，不能把全部会话一次性返回给前端");
        ChatDtos.SessionSummaryVO row = rows.get(0);
        assertEquals(9L, row.sessionId());
        assertEquals("ACTIVE", row.status());
        assertEquals(9003L, row.staffId());
        assertEquals(3_000L, row.createdAtMillis());
        assertEquals(4_000L, row.lastActiveAtMillis());
    }

    @Test
    void listSessionsClampsLimitAndReturnsEmptyForNoHistory() {
        when(sessionRepository.findByCustomerId(9002L)).thenReturn(List.of());

        assertTrue(queryService.listSessions(9002L, 0).isEmpty());
        verify(sessionRepository).findByCustomerId(9002L);
    }

    @Test
    void currentOpenReturnsExistingUnclosedSession() {
        ChatSession waiting = new ChatSession(7L, 9002L, false, ChatSessionStatus.WAITING, null, 1L, 1L, 0);
        when(sessionRepository.findByCustomerId(9002L)).thenReturn(java.util.List.of(waiting));

        ChatDtos.SessionVO vo = queryService.currentOpen(9002L);

        assertEquals(7L, vo.sessionId());
        assertEquals("WAITING", vo.status());
        verify(sessionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void currentOpenIgnoresClosedSessions() {
        ChatSession closed = new ChatSession(7L, 9002L, false, ChatSessionStatus.CLOSED, 9001L, 1L, 1L, 0);
        ChatSession active = new ChatSession(8L, 9002L, ChatSessionStatus.ACTIVE, 9001L);
        when(sessionRepository.findByCustomerId(9002L)).thenReturn(java.util.List.of(closed, active));

        ChatDtos.SessionVO vo = queryService.currentOpen(9002L);

        assertEquals(8L, vo.sessionId(), "已结束会话不能被当成当前会话复用，否则客户发消息会被 STATE_CONFLICT 拒绝");
    }

    // ---------------- P0-04：历史消息归属校验 ----------------

    @Test
    void historyRejectsOtherCustomer() {
        when(sessionRepository.findById(33L))
                .thenReturn(Optional.of(new ChatSession(33L, 9002L, ChatSessionStatus.ACTIVE, 9001L)));

        BizException ex = assertThrows(BizException.class,
                () -> queryService.history(33L, 0, 9003L));

        assertEquals(com.sent.sports.common.error.ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(chatMessageRepository, never()).findBySessionId(33L);
    }

    @Test
    void historyAllowsSessionOwner() {
        when(sessionRepository.findById(33L))
                .thenReturn(Optional.of(new ChatSession(33L, 9002L, ChatSessionStatus.ACTIVE, 9001L)));
        when(chatMessageRepository.findBySessionId(33L)).thenReturn(List.of(
                new ChatMessage("MSG1", 33L, SenderType.CUSTOMER, "a", "c1", 1, 1000L)));

        List<ChatDtos.MessageVO> rows = queryService.history(33L, 0, 9002L);

        assertEquals(1, rows.size());
    }

    @Test
    void historyForStaffSkipsOwnershipCheck() {
        // 客服工作台是共享队列：排队中的会话还没分配，坐席必须先能看到历史才能接待
        when(chatMessageRepository.findBySessionId(33L)).thenReturn(List.of(
                new ChatMessage("MSG1", 33L, SenderType.CUSTOMER, "a", "c1", 1, 1000L)));

        List<ChatDtos.MessageVO> rows = queryService.history(33L, 0, null);

        assertEquals(1, rows.size());
        verify(sessionRepository, never()).findById(33L);
    }

    @Test
    void historyRejectsWhenSessionMissing() {
        when(sessionRepository.findById(404L)).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class,
                () -> queryService.history(404L, 0, 9002L));

        assertEquals(com.sent.sports.common.error.ErrorCode.NOT_FOUND, ex.getErrorCode());
    }
}
