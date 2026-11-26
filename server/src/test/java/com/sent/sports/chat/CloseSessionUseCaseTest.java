package com.sent.sports.chat;

import com.sent.sports.chat.domain.AgentPresence;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.PresenceStatus;
import com.sent.sports.chat.mapper.po.ChatSessionNotePO;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.ChatNotifier;
import com.sent.sports.chat.service.CloseSessionUseCase;
import com.sent.sports.chat.service.SendSatisfactionCardUseCase;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结束会话用例测试（P3-56/57，TDD 红）：主动关闭(原因枚举)、超时自动关闭(10min)、释放名额。
 */
class CloseSessionUseCaseTest extends BaseUseCaseTest {

    private static final long NOW = 1_000_000_000L;
    private static final long TIMEOUT = 10L * 60 * 1000; // 10 min

    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private AgentPresenceRepository presenceRepository;
    @Mock
    private ChatNotifier notifier;
    @Mock
    private SendSatisfactionCardUseCase satisfactionCardUseCase;
    @InjectMocks
    private CloseSessionUseCase useCase;
    @Captor
    private ArgumentCaptor<ChatSession> sessionCaptor;

    /** 只给「草稿也能结束会话」这条用例造一份填了一半的工单。 */
    private static ChatSessionNotePO completeTicket() {
        ChatSessionNotePO po = new ChatSessionNotePO();
        po.setSummary("客户咨询定制足球印字");
        po.setCategory("INQUIRY");
        po.setIssueInfo("CLEAR");
        po.setResult("DONE");
        po.setSolution("已告知起订量与交期");
        return po;
    }

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

    /**
     * 定时任务 10 分钟自动结束的落库出口（2026-11-16 用户要求）。
     * <p>必须同时释放坐席名额并补位：只把行改成 CLOSED 的话，负载会一直挂着，
     * 跑几轮之后 9001 就被「幽灵会话」判满，新客户只能排队、工作台看起来像客服掉线。</p>
     */
    @Test
    void timeoutCloseReleasesSeatAndPromotesWaiting() {
        ChatSession active = new ChatSession(1L, 100L, false, ChatSessionStatus.ACTIVE, 9001L, NOW, NOW, 0);
        ChatSession waiting = new ChatSession(2L, 200L, false, ChatSessionStatus.WAITING, null, NOW, NOW, 0);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(active));
        when(sessionRepository.findWaiting()).thenReturn(List.of(waiting));
        AgentPresence presence = new AgentPresence(9001L, PresenceStatus.ONLINE, 1, 3, 0L);
        when(presenceRepository.findByStaffId(9001L)).thenReturn(Optional.of(presence));

        assertTrue(useCase.timeoutClose(1L));

        assertEquals(ChatSessionStatus.CLOSED, active.getStatus());
        assertEquals("TIMEOUT", active.getCloseReason());
        assertEquals(ChatSessionStatus.ACTIVE, waiting.getStatus());
        assertEquals(1, presence.getActiveSessions()); // 关闭 -1 后补位 +1，名额没有凭空多出来
        verify(notifier).sessionAssigned(200L, 2L, 9001L);
    }

    /** 排队中的会话被超时结束：不占名额，也无需补位。 */
    @Test
    void timeoutCloseWaitingDoesNotTouchPresence() {
        ChatSession waiting = new ChatSession(3L, 300L, false, ChatSessionStatus.WAITING, null, NOW, NOW, 0);
        when(sessionRepository.findById(3L)).thenReturn(Optional.of(waiting));

        assertTrue(useCase.timeoutClose(3L));

        assertEquals(ChatSessionStatus.CLOSED, waiting.getStatus());
        verify(presenceRepository, never()).findByStaffId(anyLong());
        verify(sessionRepository, never()).findWaiting();
    }

    /** 已被人工结束的会话：返回 false，不重复关闭、不重复扣名额。 */
    @Test
    void timeoutCloseAlreadyClosedReturnsFalse() {
        ChatSession closed = new ChatSession(4L, 400L, false, ChatSessionStatus.CLOSED, 9001L, NOW, NOW, 0);
        when(sessionRepository.findById(4L)).thenReturn(Optional.of(closed));

        assertFalse(useCase.timeoutClose(4L));

        verify(sessionRepository, never()).save(any());
        verify(presenceRepository, never()).findByStaffId(anyLong());
    }

    @Test
    void timeoutCloseMissingSessionReturnsFalse() {
        when(sessionRepository.findById(9L)).thenReturn(Optional.empty());

        assertFalse(useCase.timeoutClose(9L));
        verify(sessionRepository, never()).save(any());
    }

    /**
     * 用户要求：客服点结束后客户必须立刻看到「本次会话已结束」，不能等下次轮询。
     * 帧只推一次：重复推送会让前端弹出两次结束提示 / 覆盖后一条消息。
     */
    @Test
    void closePushesClosedFrameExactlyOnce() {
        ChatSession active = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 9001L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(active));

        useCase.close(new CloseCmd(1L, "DONE", 9001L));

        verify(notifier, times(1)).sessionClosed(100L, 1L, 9001L, "DONE");
    }

    /** 已结束会话重复关闭直接拒绝，不得再次推帧（幂等）。 */
    @Test
    void repeatedCloseDoesNotPushFrameTwice() {
        ChatSession closed = new ChatSession(1L, 100L, ChatSessionStatus.CLOSED, 9001L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(closed));

        assertThrows(BizException.class, () -> useCase.close(new CloseCmd(1L, "DONE", 9001L)));

        verify(notifier, never()).sessionClosed(anyLong(), anyLong(), any(), any());
    }

    /** 超时结束同样要推帧；重复扫描到已结束会话时跳过，不能反复打扰客户。 */
    @Test
    void timeoutClosePushesFrameOnceAndSkipsSecondScan() {
        ChatSession active = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 9001L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(active));

        assertTrue(useCase.timeoutClose(1L));
        assertFalse(useCase.timeoutClose(1L));

        verify(notifier, times(1)).sessionClosed(100L, 1L, 9001L, "TIMEOUT");
    }

    /** 通知失败只记日志，不能阻断关单与队列补位。 */
    @Test
    void notificationFailureDoesNotBlockClose() {
        ChatSession active = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 9001L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(active));
        org.mockito.Mockito.doThrow(new RuntimeException("ws down"))
                .when(notifier).sessionClosed(anyLong(), anyLong(), any(), any());

        CloseSessionResult r = useCase.close(new CloseCmd(1L, "DONE", 9001L));

        assertEquals(ChatSessionStatus.CLOSED, r.status());
        verify(sessionRepository).save(active);
    }

    /**
     * 2026-11-26 用户口径变更：结束会话不再强制工单。
     * 客服可以先结束与客户的对话，再在空闲时补工单；真正「关闭会话窗口」由前端按提交状态拦截。
     */
    @Test
    void closeWithoutServiceTicketAllowed() {
        ChatSession active = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 9001L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(active));

        CloseSessionResult r = useCase.close(new CloseCmd(1L, "DONE", 9001L));

        assertEquals(ChatSessionStatus.CLOSED, r.status());
        verify(sessionRepository).save(active);
        verify(notifier).sessionClosed(anyLong(), anyLong(), any(), any());
    }

    /** 草稿（填了一半）同样不拦：只有「提交」才是审查入口，结束会话不该被半成品卡住。 */
    @Test
    void closeWithDraftTicketAllowed() {
        ChatSession active = new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 9001L);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(active));
        ChatSessionNotePO draft = completeTicket();
        draft.setSolution(null);
        draft.setSubmitStatus("DRAFT");

        CloseSessionResult r = useCase.close(new CloseCmd(1L, "DONE", 9001L));

        assertEquals(ChatSessionStatus.CLOSED, r.status());
        verify(sessionRepository).save(active);
    }

    /** 超时自动结束不要求工单：客户已离线，客服根本补不了单，不能让会话永远关不掉。 */
    @Test
    void timeoutCloseDoesNotRequireTicket() {
        ChatSession active = new ChatSession(1L, 100L, false, ChatSessionStatus.ACTIVE, 9001L,
                NOW - 40 * 60 * 1000, NOW - 40 * 60 * 1000, 0);
        when(sessionRepository.findById(1L)).thenReturn(Optional.of(active));

        assertTrue(useCase.timeoutClose(1L));

        verify(sessionRepository).save(active);
    }
}
