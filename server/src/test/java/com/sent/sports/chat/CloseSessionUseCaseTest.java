package com.sent.sports.chat;

import com.sent.sports.chat.domain.AgentPresence;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.PresenceStatus;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.ChatNotifier;
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
import static org.mockito.ArgumentMatchers.anyLong;
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
    @Mock
    private AgentPresenceRepository presenceRepository;
    @Mock
    private ChatNotifier notifier;
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

    /**
     * 自动补位（验收核心）：客服满负荷 3 人时结束一个会话，
     * 排队最早的那位客户必须立刻转 ACTIVE，且客服负载「3 → 2 → 3」保持不变。
     */
    @Test
    void closePromotesFirstWaitingAndKeepsLoadFull() {
        ChatSession closing = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 9001L);
        // 排队：9004 早于 9005，但 9005 被分配给别的客服（8888）→ 不该被补位
        ChatSession waitingLater = new ChatSession(4L, 9004L, false, ChatSessionStatus.WAITING, null, 2_000L, 2_000L, 0);
        ChatSession waitingEarliest = new ChatSession(2L, 9003L, false, ChatSessionStatus.WAITING, null, 1_000L, 1_000L, 0);
        ChatSession waitingOtherStaff = new ChatSession(3L, 9005L, false, ChatSessionStatus.WAITING, 8888L, 1_500L, 1_500L, 0);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(closing));
        when(sessionRepository.findWaiting()).thenReturn(List.of(waitingLater, waitingOtherStaff, waitingEarliest));
        AgentPresence presence = new AgentPresence(9001L, PresenceStatus.ONLINE, 3, 3, 0L);
        when(presenceRepository.findByStaffId(9001L)).thenReturn(Optional.of(presence));

        CloseSessionResult r = useCase.close(new CloseCmd(1L, "DONE", 9001L));

        assertEquals(ChatSessionStatus.ACTIVE, waitingEarliest.getStatus(), "排队最早的客户应被自动补位");
        assertEquals(9001L, waitingEarliest.getStaffId(), "补位会话应分配给刚释放名额的客服");
        assertEquals(ChatSessionStatus.WAITING, waitingLater.getStatus(), "后到的客户应继续排队");
        assertEquals(ChatSessionStatus.WAITING, waitingOtherStaff.getStatus(), "分配给其他客服的排队会话不参与补位");
        assertEquals(3, presence.getActiveSessions(), "负载应保持为 3（先 -1 再 +1）");
        verify(notifier).sessionAssigned(9003L, 2L, 9001L);
        assertEquals(2L, r.promotedSessionId());
    }

    /** 结束排队中的会话不释放名额，也不触发补位。 */
    @Test
    void closeWaitingSessionDoesNotPromote() {
        ChatSession waiting = new ChatSession(5L, 200L, false, ChatSessionStatus.WAITING, null, 1_000L, 1_000L, 0);
        when(sessionRepository.findById(5L)).thenReturn(Optional.of(waiting));

        CloseSessionResult r = useCase.close(new CloseCmd(5L, "LEFT", 9001L));

        verify(sessionRepository, never()).findWaiting();
        verify(presenceRepository, never()).findByStaffId(anyLong());
        verify(notifier, never()).sessionAssigned(anyLong(), anyLong(), any());
        assertEquals(null, r.promotedSessionId());
    }

    /** 队列为空时只扣减负载，不补位、不推送。 */
    @Test
    void closeWithoutWaitingOnlyFreesSlot() {
        ChatSession closing = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 9001L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(closing));
        when(sessionRepository.findWaiting()).thenReturn(List.of());
        AgentPresence presence = new AgentPresence(9001L, PresenceStatus.ONLINE, 3, 3, 0L);
        when(presenceRepository.findByStaffId(9001L)).thenReturn(Optional.of(presence));

        useCase.close(new CloseCmd(1L, "DONE", 9001L));

        assertEquals(2, presence.getActiveSessions());
        verify(notifier, never()).sessionAssigned(anyLong(), anyLong(), any());
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
