package com.sent.sports.chat;

import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.dto.ChatDtos;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.service.impl.ChatQueryServiceImpl;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 会话查询实现测试：历史消息按 afterSeq 增量返回（此前为恒空桩，会把前端气泡刷没）。
 */
class ChatQueryServiceTest extends BaseUseCaseTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

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
}
