package com.sent.sports.chat;

import com.sent.sports.chat.domain.AgentPresence;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.PresenceStatus;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.CreateSessionUseCase;
import com.sent.sports.chat.service.dto.CreateSessionCmd;
import com.sent.sports.chat.service.dto.CreateSessionResult;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发起会话用例测试（P3-50/51，TDD 红）：验收 A17/A20/A22——
 * 已有进行中会话则复用、有客服则 ACTIVE、无客服或达上限则 WAITING 进排队。
 */
class CreateSessionUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000_000L);

    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private AgentPresenceRepository presenceRepository;
    @Mock
    private Clock clock;
    @InjectMocks
    private CreateSessionUseCase useCase;
    @Captor
    private ArgumentCaptor<ChatSession> sessionCaptor;

    @Test
    void reusesExistingActiveSession() {
        ChatSession active = new ChatSession(10L, 7L, ChatSessionStatus.ACTIVE, 5L);
        when(sessionRepository.findByCustomerId(7L)).thenReturn(List.of(active));

        CreateSessionResult r = useCase.create(new CreateSessionCmd(7L, false, 1L));

        assertTrue(r.reused());
        assertEquals(10L, r.sessionId());
        assertEquals(ChatSessionStatus.ACTIVE, r.status());
        assertEquals(5L, r.staffId());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void reusesExistingWaitingSession() {
        // 用户重复进入客服页不得堆积多条 WAITING 会话（否则工作台出现多条同客户待接入）
        ChatSession waiting = new ChatSession(11L, 7L, ChatSessionStatus.WAITING, null);
        when(sessionRepository.findByCustomerId(7L)).thenReturn(List.of(waiting));

        CreateSessionResult r = useCase.create(new CreateSessionCmd(7L, false, 1L));

        assertTrue(r.reused());
        assertEquals(11L, r.sessionId());
        assertEquals(ChatSessionStatus.WAITING, r.status());
        verify(sessionRepository, never()).save(any());
    }

    /**
     * 单坐席纠正（2026-11-16 用户实测「发你好没人理、刷新也没有」）：
     * 客户复用的 ACTIVE 会话被别的客服（9003）占着时，9001 工作台按 staff_id 过滤根本看不见，
     * 客户消息等于发进黑洞。所以复用路径要把这类会话交还 9001，并同步两边接待名额。
     */
    @Test
    void reusedActiveSessionOwnedByOtherStaffIsHandedTo9001() {
        ChatSession active = new ChatSession(12L, 7L, ChatSessionStatus.ACTIVE, 9003L);
        AgentPresence other = new AgentPresence(9003L, PresenceStatus.ONLINE, 1, 3, 0L);
        AgentPresence seat = new AgentPresence(9001L, PresenceStatus.ONLINE, 0, 3, 0L);
        when(sessionRepository.findByCustomerId(7L)).thenReturn(List.of(active));
        when(presenceRepository.findByStaffId(9003L)).thenReturn(Optional.of(other));
        when(presenceRepository.findByStaffId(9001L)).thenReturn(Optional.of(seat));

        CreateSessionResult r = useCase.create(new CreateSessionCmd(7L, false, 1L));

        assertTrue(r.reused());
        assertEquals(12L, r.sessionId());
        assertEquals(ChatSessionStatus.ACTIVE, r.status());
        assertEquals(9001L, r.staffId(), "被其他客服占住的会话应交还 9001");
        verify(sessionRepository).save(sessionCaptor.capture());
        assertEquals(9001L, sessionCaptor.getValue().getStaffId());
        assertEquals(0, other.getActiveSessions(), "原接待人应释放一个名额");
        assertEquals(1, seat.getActiveSessions(), "9001 应占用一个名额");
    }

    /** 9001 不在线时不动别人的会话：等他上线/接管，避免把会话改成「没人接待」的悬空状态。 */
    @Test
    void reusedActiveSessionKeptWhenSingleSeatOffline() {
        ChatSession active = new ChatSession(13L, 7L, ChatSessionStatus.ACTIVE, 9003L);
        when(sessionRepository.findByCustomerId(7L)).thenReturn(List.of(active));
        when(presenceRepository.findByStaffId(9001L))
                .thenReturn(Optional.of(new AgentPresence(9001L, PresenceStatus.OFFLINE, 0, 3, 0L)));

        CreateSessionResult r = useCase.create(new CreateSessionCmd(7L, false, 1L));

        assertEquals(9003L, r.staffId());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void createsActiveWhenStaffAvailable() {
        when(sessionRepository.findByCustomerId(7L)).thenReturn(List.of());
        when(presenceRepository.findAll()).thenReturn(List.of(
                new AgentPresence(9001L, PresenceStatus.ONLINE, 0, 10, 0L)));
        when(presenceRepository.findByStaffId(9001L))
                .thenReturn(Optional.of(new AgentPresence(9001L, PresenceStatus.ONLINE, 0, 10, 0L)));
        when(sessionRepository.countActive()).thenReturn(0);
        when(sessionRepository.nextId()).thenReturn(100L);
        when(clock.instant()).thenReturn(NOW);

        CreateSessionResult r = useCase.create(new CreateSessionCmd(7L, false, 1L));

        verify(sessionRepository).save(sessionCaptor.capture());
        ChatSession saved = sessionCaptor.getValue();
        assertEquals(ChatSessionStatus.ACTIVE, saved.getStatus());
        assertEquals(9001L, saved.getStaffId());
        assertEquals(100L, r.sessionId());
        assertEquals(ChatSessionStatus.ACTIVE, r.status());
        assertEquals(9001L, r.staffId());
    }

    @Test
    void createsWaitingWhenNoStaff() {
        when(sessionRepository.findByCustomerId(7L)).thenReturn(List.of());
        when(presenceRepository.findAll()).thenReturn(List.of());
        when(sessionRepository.nextId()).thenReturn(100L);
        when(clock.instant()).thenReturn(NOW);

        CreateSessionResult r = useCase.create(new CreateSessionCmd(7L, false, 1L));

        verify(sessionRepository).save(sessionCaptor.capture());
        assertEquals(ChatSessionStatus.WAITING, sessionCaptor.getValue().getStatus());
        assertEquals(ChatSessionStatus.WAITING, r.status());
        assertEquals(null, r.staffId());
    }

    @Test
    void createsWaitingWhenOverCapacity() {
        when(sessionRepository.findByCustomerId(7L)).thenReturn(List.of());
        when(presenceRepository.findAll()).thenReturn(List.of(
                new AgentPresence(9001L, PresenceStatus.ONLINE, 0, 10, 0L)));
        when(sessionRepository.countActive()).thenReturn(CreateSessionUseCase.MAX_CONCURRENT);
        when(sessionRepository.nextId()).thenReturn(100L);
        when(clock.instant()).thenReturn(NOW);

        CreateSessionResult r = useCase.create(new CreateSessionCmd(7L, false, 1L));

        verify(sessionRepository).save(sessionCaptor.capture());
        assertEquals(ChatSessionStatus.WAITING, sessionCaptor.getValue().getStatus());
        assertEquals(ChatSessionStatus.WAITING, r.status());
    }

    /** 坐席已满 3 人时必须排队（不再分配给该客服）。 */
    @Test
    void createsWaitingWhenOnlyStaffReachedMaxSessions() {
        when(sessionRepository.findByCustomerId(7L)).thenReturn(List.of());
        when(presenceRepository.findAll()).thenReturn(List.of(
                new AgentPresence(9001L, PresenceStatus.ONLINE, 3, 3, 0L)));
        when(sessionRepository.countActive()).thenReturn(3);
        when(sessionRepository.nextId()).thenReturn(100L);
        when(clock.instant()).thenReturn(NOW);

        CreateSessionResult r = useCase.create(new CreateSessionCmd(7L, false, 1L));

        assertEquals(ChatSessionStatus.WAITING, r.status());
        assertEquals(null, r.staffId());
        verify(presenceRepository, never()).findByStaffId(anyLong());
    }

    /**
     * 单坐席（2026-11-16）：系统只用 9001 接待。
     * 其他客服即使在线也不参与自动分配，新会话必须落到 9001。
     */
    @Test
    void singleSeatOnlyAssignsTo9001EvenIfOtherStaffOnline() {
        when(sessionRepository.findByCustomerId(7L)).thenReturn(List.of());
        when(presenceRepository.findAll()).thenReturn(List.of(
                new AgentPresence(9003L, PresenceStatus.ONLINE, 0, 10, 0L),
                new AgentPresence(9001L, PresenceStatus.ONLINE, 0, 10, 0L)));
        when(presenceRepository.findByStaffId(9001L))
                .thenReturn(Optional.of(new AgentPresence(9001L, PresenceStatus.ONLINE, 0, 10, 0L)));
        when(sessionRepository.countActive()).thenReturn(0);
        when(sessionRepository.nextId()).thenReturn(100L);
        when(clock.instant()).thenReturn(NOW);

        CreateSessionResult r = useCase.create(new CreateSessionCmd(7L, false, 1L));

        verify(sessionRepository).save(sessionCaptor.capture());
        assertEquals(ChatSessionStatus.ACTIVE, sessionCaptor.getValue().getStatus());
        assertEquals(9001L, sessionCaptor.getValue().getStaffId());
        assertEquals(9001L, r.staffId());
        verify(presenceRepository).findByStaffId(9001L);
    }

    /** 单坐席：只有 9003 在线而 9001 离线时，会话进排队，不能分给 9003。 */
    @Test
    void otherStaffOnlineButSingleSeatOfflineGoesWaiting() {
        when(sessionRepository.findByCustomerId(7L)).thenReturn(List.of());
        when(presenceRepository.findAll()).thenReturn(List.of(
                new AgentPresence(9003L, PresenceStatus.ONLINE, 0, 10, 0L)));
        when(sessionRepository.nextId()).thenReturn(100L);
        when(clock.instant()).thenReturn(NOW);

        CreateSessionResult r = useCase.create(new CreateSessionCmd(7L, false, 1L));

        verify(sessionRepository).save(sessionCaptor.capture());
        assertEquals(ChatSessionStatus.WAITING, sessionCaptor.getValue().getStatus());
        assertEquals(null, r.staffId());
        verify(presenceRepository, never()).findByStaffId(anyLong());
    }

    /** 分配成功后必须把客服负载 +1，否则并发上限永远判不满。 */
    @Test
    void assigningIncrementsStaffActiveSessions() {
        AgentPresence presence = new AgentPresence(9001L, PresenceStatus.ONLINE, 1, 3, 0L);
        when(sessionRepository.findByCustomerId(7L)).thenReturn(List.of());
        when(presenceRepository.findAll()).thenReturn(List.of(presence));
        when(presenceRepository.findByStaffId(9001L)).thenReturn(Optional.of(presence));
        when(sessionRepository.countActive()).thenReturn(1);
        when(sessionRepository.nextId()).thenReturn(100L);
        when(clock.instant()).thenReturn(NOW);

        useCase.create(new CreateSessionCmd(7L, false, 1L));

        assertEquals(2, presence.getActiveSessions());
        verify(presenceRepository).save(presence);
    }
}
