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
    void historySkipsMessagesAtOrBeforeAfterSeq() {
        when(chatMessageRepository.findBySessionId(33L)).thenReturn(List.of(
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
}
