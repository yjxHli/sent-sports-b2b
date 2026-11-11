package com.sent.sports.chat;

import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.AssignSessionUseCase;
import com.sent.sports.chat.service.dto.AssignCmd;
import com.sent.sports.chat.service.dto.AssignSessionResult;
import com.sent.sports.chat.service.dto.TransferCmd;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分配与转接用例测试（P3-54/55，TDD 红）：排队优先 VIP(再按最早)，
 * 转接需非空原因；历史消息保留(不删除)。
 */
class AssignSessionUseCaseTest extends BaseUseCaseTest {

    @Mock
    private ChatSessionRepository sessionRepository;
    @InjectMocks
    private AssignSessionUseCase useCase;
    @Captor
    private ArgumentCaptor<ChatSession> sessionCaptor;

    @Test
    void assignTargetsGivenSession() {
        // 工作台点「接待」必须接待用户看到的那一条：即使队列里还有更早的会话也不能串台。
        ChatSession target = new ChatSession(42L, 200L, false, ChatSessionStatus.WAITING, null, 500L, 500L, 0);
        when(sessionRepository.findById(42L)).thenReturn(Optional.of(target));

        AssignSessionResult r = useCase.assign(new AssignCmd(42L, 9L, 1L));

        verify(sessionRepository).save(sessionCaptor.capture());
        assertEquals(42L, sessionCaptor.getValue().getId());
        assertEquals(9L, sessionCaptor.getValue().getStaffId());
        assertEquals(ChatSessionStatus.ACTIVE, r.status());
        verify(sessionRepository, never()).findWaiting();
    }

    @Test
    void assignMissingSessionRejects() {
        when(sessionRepository.findById(404L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class, () -> useCase.assign(new AssignCmd(404L, 9L, 1L)));

        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void assignNonWaitingSessionRejects() {
        ChatSession active = new ChatSession(43L, 200L, false, ChatSessionStatus.ACTIVE, 5L, 500L, 500L, 0);
        when(sessionRepository.findById(43L)).thenReturn(Optional.of(active));

        BizException e = assertThrows(BizException.class, () -> useCase.assign(new AssignCmd(43L, 9L, 1L)));

        assertEquals(ErrorCode.STATE_CONFLICT, e.getErrorCode());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void assignPicksVipFirst() {
        ChatSession nonVip = new ChatSession(1L, 100L, false, ChatSessionStatus.WAITING, null, 100L, 100L, 0);
        ChatSession vip = new ChatSession(2L, 200L, true, ChatSessionStatus.WAITING, null, 200L, 200L, 0);
        when(sessionRepository.findWaiting()).thenReturn(List.of(nonVip, vip));

        AssignSessionResult r = useCase.assign(new AssignCmd(9L, 1L));

        verify(sessionRepository).save(sessionCaptor.capture());
        assertEquals(2L, sessionCaptor.getValue().getId());
        assertEquals(ChatSessionStatus.ACTIVE, r.status());
        assertEquals(9L, r.staffId());
    }

    @Test
    void assignOldestWhenNoVip() {
        ChatSession newer = new ChatSession(1L, 100L, false, ChatSessionStatus.WAITING, null, 100L, 100L, 0);
        ChatSession older = new ChatSession(2L, 100L, false, ChatSessionStatus.WAITING, null, 50L, 50L, 0);
        when(sessionRepository.findWaiting()).thenReturn(List.of(newer, older));

        AssignSessionResult r = useCase.assign(new AssignCmd(9L, 1L));

        verify(sessionRepository).save(sessionCaptor.capture());
        assertEquals(2L, sessionCaptor.getValue().getId());
        assertEquals(ChatSessionStatus.ACTIVE, r.status());
    }

    @Test
    void assignEmptyQueueRejects() {
        when(sessionRepository.findWaiting()).thenReturn(List.of());

        BizException e = assertThrows(BizException.class, () -> useCase.assign(new AssignCmd(9L, 1L)));
        assertEquals(ErrorCode.BIZ_REJECT, e.getErrorCode());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void transferRequiresNonBlankReason() {
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 5L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        BizException e = assertThrows(BizException.class,
                () -> useCase.transfer(new TransferCmd(1L, 9L, "  ", 1L)));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void transferSuccessKeepsActiveAndChangesStaff() {
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 5L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        AssignSessionResult r = useCase.transfer(new TransferCmd(1L, 9L, "handover", 1L));

        verify(sessionRepository).save(sessionCaptor.capture());
        assertEquals(9L, sessionCaptor.getValue().getStaffId());
        assertEquals(ChatSessionStatus.ACTIVE, r.status());
        assertEquals(9L, r.staffId());
    }

    @Test
    void transferNonActiveRejects() {
        ChatSession session = new ChatSession(1L, 100L, ChatSessionStatus.WAITING, 5L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(session));

        BizException e = assertThrows(BizException.class,
                () -> useCase.transfer(new TransferCmd(1L, 9L, "handover", 1L)));
        assertEquals(ErrorCode.STATE_CONFLICT, e.getErrorCode());
        verify(sessionRepository, never()).save(any());
    }
}
