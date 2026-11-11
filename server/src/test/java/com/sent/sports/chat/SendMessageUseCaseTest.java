package com.sent.sports.chat;

import com.sent.sports.chat.domain.ChatMessage;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.repository.ChatMessageRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.SendMessageUseCase;
import com.sent.sports.chat.service.dto.SendMessageCmd;
import com.sent.sports.chat.service.dto.SendMessageResult;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发送消息用例测试（P3-52/53，TDD 红）：幂等(同 clientMsgId 返回原 seq)、
 * 会话已结束(CLOSED)拒绝(STATE_CONFLICT)、seq 递增。
 */
class SendMessageUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000_000L);

    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatMessageRepository messageRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private Clock clock;
    @InjectMocks
    private SendMessageUseCase useCase;
    @Captor
    private ArgumentCaptor<ChatMessage> messageCaptor;

    @Test
    void incrementsSeqForEachNewMessage() {
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 5L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySessionAndClientMsgId(anyLong(), anyString())).thenReturn(Optional.empty());
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("CM")).thenReturn("CM1");

        SendMessageResult r1 = useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, "hi", "c1", 1L));
        SendMessageResult r2 = useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, "again", "c2", 1L));

        assertEquals(1, r1.seq());
        assertEquals(2, r2.seq());
        verify(messageRepository, times(2)).save(messageCaptor.capture());
        ChatMessage last = messageCaptor.getValue();
        assertEquals("CM1", last.getMessageId());
        assertEquals(1L, last.getSessionId());
        assertEquals(SenderType.CUSTOMER, last.getSenderType());
        assertEquals("again", last.getContent());
        assertEquals("c2", last.getClientMsgId());
        assertEquals(2, last.getSeq());
        assertEquals(NOW.toEpochMilli(), last.getCreatedAtMillis());
    }

    @Test
    void idempotentReturnsExistingSeq() {
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 5L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("CM")).thenReturn("CM1");

        SendMessageResult first = useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, "hi", "dup", 1L));
        verify(messageRepository).save(messageCaptor.capture());
        ChatMessage saved = messageCaptor.getValue();

        when(messageRepository.findBySessionAndClientMsgId(anyLong(), anyString())).thenReturn(Optional.of(saved));
        SendMessageResult second = useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, "hi", "dup", 1L));

        assertEquals(1, first.seq());
        assertEquals("CM1", first.messageId());
        assertEquals(1, second.seq());
        assertEquals("CM1", second.messageId());
        verify(messageRepository, times(1)).save(any());
    }

    @Test
    void closedSessionRejected() {
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.CLOSED, 5L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        BizException e = assertThrows(BizException.class,
                () -> useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, "hi", "c1", 1L)));
        assertEquals(ErrorCode.STATE_CONFLICT, e.getErrorCode());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void notFoundThrows() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class,
                () -> useCase.send(new SendMessageCmd(1L, SenderType.CUSTOMER, "hi", "c1", 1L)));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verify(messageRepository, never()).save(any());
    }
}
