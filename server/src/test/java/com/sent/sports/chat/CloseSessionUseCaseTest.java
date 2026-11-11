package com.sent.sports.chat;

import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.CloseSessionUseCase;
import com.sent.sports.chat.service.dto.CloseCmd;
import com.sent.sports.chat.service.dto.CloseExpiredResult;
import com.sent.sports.chat.service.dto.CloseSessionResult;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结束会话用例测试（P3-56/57，TDD 红）：主动关闭(原因枚举)、超时自动关闭(30min)、释放名额。
 */
class CloseSessionUseCaseTest extends BaseUseCaseTest {

    private static final long NOW = 1_000_000_000L;
    private static final long TIMEOUT = 30L * 60 * 1000; // 30 min

    @Mock
    private ChatSessionRepository sessionRepository;
    @InjectMocks
    private CloseSessionUseCase useCase;
    @Captor
    private ArgumentCaptor<ChatSession> sessionCaptor;

    @Test
    void closeWithValidReason() {
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 5L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        CloseSessionResult r = useCase.close(new CloseCmd(1L, "DONE", 1L));

        verify(sessionRepository).save(sessionCaptor.capture());
        assertEquals(ChatSessionStatus.CLOSED, sessionCaptor.getValue().getStatus());
        assertEquals("DONE", sessionCaptor.getValue().getCloseReason());
        assertEquals(ChatSessionStatus.CLOSED, r.status());
    }

    @Test
    void closeInvalidReasonRejected() {
        BizException e = assertThrows(BizException.class,
                () -> useCase.close(new CloseCmd(1L, "BAD", 1L)));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void closeNotFound() {
        when(sessionRepository.findById(1L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class,
                () -> useCase.close(new CloseCmd(1L, "DONE", 1L)));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void closeAlreadyClosedRejected() {
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.CLOSED, 5L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        BizException e = assertThrows(BizException.class,
                () -> useCase.close(new CloseCmd(1L, "DONE", 1L)));
        assertEquals(ErrorCode.STATE_CONFLICT, e.getErrorCode());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void closeExpiredClosesOnlyInactive() {
        ChatSession activeOld = new ChatSession(1L, 100L, false, ChatSessionStatus.ACTIVE, 5L, NOW - 40 * 60 * 1000, NOW - 40 * 60 * 1000, 0);
        ChatSession activeFresh = new ChatSession(2L, 100L, false, ChatSessionStatus.ACTIVE, 5L, NOW - 5 * 60 * 1000, NOW - 5 * 60 * 1000, 0);
        ChatSession waitingOld = new ChatSession(3L, 100L, false, ChatSessionStatus.WAITING, null, NOW - 40 * 60 * 1000, NOW - 40 * 60 * 1000, 0);
        when(sessionRepository.findByStatus(ChatSessionStatus.ACTIVE)).thenReturn(List.of(activeOld, activeFresh));
        when(sessionRepository.findByStatus(ChatSessionStatus.WAITING)).thenReturn(List.of(waitingOld));

        CloseExpiredResult r = useCase.closeExpired(NOW);

        verify(sessionRepository, times(2)).save(sessionCaptor.capture());
        assertEquals(2, r.closedCount());
        assertEquals(List.of(1L, 3L), r.closedSessionIds());
    }
}
