package com.sent.sports.chat;

import com.sent.sports.chat.domain.AgentPresence;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.PresenceStatus;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.ChatNotifier;
import com.sent.sports.chat.service.PresenceUseCase;
import com.sent.sports.chat.service.dto.PresenceResult;
import com.sent.sports.chat.service.dto.SetPresenceCmd;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 在线状态切换用例测试（P3-58/59，TDD 红）：ONLINE/BUSY/OFFLINE，
 * 离线释放会话(ACTIVE → WAITING 回到队列)。
 */
class PresenceUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000_000L);

    @Mock
    private AgentPresenceRepository presenceRepository;
    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private ChatNotifier notifier;
    @Mock
    private Clock clock;
    @InjectMocks
    private PresenceUseCase useCase;
    @Captor
    private ArgumentCaptor<AgentPresence> presenceCaptor;
    @Captor
    private ArgumentCaptor<ChatSession> sessionCaptor;

    @Test
    void offlineReleasesActiveSessions() {
        when(presenceRepository.findByStaffId(5L)).thenReturn(Optional.empty());
        when(clock.instant()).thenReturn(NOW);
        when(sessionRepository.findActiveByStaff(5L)).thenReturn(
                List.of(new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 5L)));

        PresenceResult r = useCase.setPresence(new SetPresenceCmd(5L, PresenceStatus.OFFLINE, 1L));

        verify(sessionRepository).save(sessionCaptor.capture());
        assertEquals(ChatSessionStatus.WAITING, sessionCaptor.getValue().getStatus());
        assertEquals(null, sessionCaptor.getValue().getStaffId());

        verify(presenceRepository).save(presenceCaptor.capture());
        AgentPresence saved = presenceCaptor.getValue();
        assertEquals(PresenceStatus.OFFLINE, saved.getPresence());
        assertEquals(NOW.toEpochMilli(), saved.getUpdatedAtMillis());
        assertEquals(1, r.releasedSessions());
    }

    @Test
    void offlineWithNoActiveSessions() {
        when(presenceRepository.findByStaffId(5L)).thenReturn(Optional.empty());
        when(clock.instant()).thenReturn(NOW);
        when(sessionRepository.findActiveByStaff(5L)).thenReturn(List.of());

        PresenceResult r = useCase.setPresence(new SetPresenceCmd(5L, PresenceStatus.OFFLINE, 1L));

        verify(sessionRepository, org.mockito.Mockito.never()).save(any(ChatSession.class));
        verify(presenceRepository).save(presenceCaptor.capture());
        assertEquals(PresenceStatus.OFFLINE, presenceCaptor.getValue().getPresence());
        assertEquals(0, r.releasedSessions());
    }

    @Test
    void onlineUpdatesPresence() {
        when(presenceRepository.findByStaffId(5L)).thenReturn(
                Optional.of(new AgentPresence(5L, PresenceStatus.BUSY, 0, 50, 0L)));
        when(clock.instant()).thenReturn(NOW);

        PresenceResult r = useCase.setPresence(new SetPresenceCmd(5L, PresenceStatus.ONLINE, 1L));

        verify(presenceRepository).save(presenceCaptor.capture());
        assertEquals(PresenceStatus.ONLINE, presenceCaptor.getValue().getPresence());
        assertEquals(0, r.releasedSessions());
    }

    /** 首次上线的坐席默认并发上限为 3，且负载以库里的 ACTIVE 会话数为准（重启后不漂移）。 */
    @Test
    void onlineSeedsLoadFromDbAndDefaultsThreeSlots() {
        when(presenceRepository.findByStaffId(5L)).thenReturn(Optional.empty());
        when(clock.instant()).thenReturn(NOW);
        when(sessionRepository.countActiveByStaff(5L)).thenReturn(2);

        useCase.setPresence(new SetPresenceCmd(5L, PresenceStatus.ONLINE, 1L));

        verify(presenceRepository).save(presenceCaptor.capture());
        assertEquals(3, presenceCaptor.getValue().getMaxSessions(), "默认并发上限应为 3");
        assertEquals(2, presenceCaptor.getValue().getActiveSessions(), "负载应以库里 ACTIVE 会话数为准");
    }

    /** 离线释放全部会话后，负载必须归零，否则补位时会误判「已满」。 */
    @Test
    void offlineResetsLoadToZero() {
        when(presenceRepository.findByStaffId(5L)).thenReturn(
                Optional.of(new AgentPresence(5L, PresenceStatus.ONLINE, 3, 3, 0L)));
        when(clock.instant()).thenReturn(NOW);
        when(sessionRepository.findActiveByStaff(5L)).thenReturn(
                List.of(new ChatSession(1L, 100L, ChatSessionStatus.ACTIVE, 5L)));

        useCase.setPresence(new SetPresenceCmd(5L, PresenceStatus.OFFLINE, 1L));

        verify(presenceRepository).save(presenceCaptor.capture());
        assertEquals(0, presenceCaptor.getValue().getActiveSessions());
    }

    /**
     * 忙碌 → 在线：排队客户必须自动补位接入（2026-11-21 用户口径）。
     *
     * <p>此前只有「结束会话」才会补位，客服忙碌时积压的队列在切回在线后纹丝不动，
     * 客户只能干等客服逐个点「接待」。</p>
     */
    @Test
    void onlinePromotesWaitingQueue() {
        when(presenceRepository.findByStaffId(5L)).thenReturn(
                Optional.of(new AgentPresence(5L, PresenceStatus.BUSY, 0, 3, 0L)));
        when(clock.instant()).thenReturn(NOW);
        when(sessionRepository.countActiveByStaff(5L)).thenReturn(0);
        ChatSession earlier = new ChatSession(1L, 100L, false, ChatSessionStatus.WAITING, null, 100L, 100L, 0);
        ChatSession later = new ChatSession(2L, 200L, false, ChatSessionStatus.WAITING, null, 200L, 200L, 0);
        when(sessionRepository.findWaiting())
                .thenReturn(List.of(earlier, later), List.of(later), List.of());

        useCase.setPresence(new SetPresenceCmd(5L, PresenceStatus.ONLINE, 1L));

        assertEquals(ChatSessionStatus.ACTIVE, earlier.getStatus(), "最早排队的客户应先接入");
        assertEquals(5L, earlier.getStaffId());
        assertEquals(ChatSessionStatus.ACTIVE, later.getStatus());
        verify(notifier).sessionAssigned(100L, 1L, 5L);
        verify(notifier).sessionAssigned(200L, 2L, 5L);
        verify(presenceRepository, org.mockito.Mockito.atLeastOnce()).save(presenceCaptor.capture());
        assertEquals(2, presenceCaptor.getValue().getActiveSessions(), "补位后负载要跟着涨，否则并发上限失效");
    }

    /** 忙碌状态不补位：队列原样保留，等切回在线再说。 */
    @Test
    void busyDoesNotPromoteWaitingQueue() {
        when(presenceRepository.findByStaffId(5L)).thenReturn(Optional.empty());
        when(clock.instant()).thenReturn(NOW);
        when(sessionRepository.countActiveByStaff(5L)).thenReturn(0);
        ChatSession waiting = new ChatSession(1L, 100L, false, ChatSessionStatus.WAITING, null, 100L, 100L, 0);

        useCase.setPresence(new SetPresenceCmd(5L, PresenceStatus.BUSY, 1L));

        assertEquals(ChatSessionStatus.WAITING, waiting.getStatus(), "忙碌时不能把排队客户拉进接待");
        verify(sessionRepository, org.mockito.Mockito.never()).save(any(ChatSession.class));
        verify(notifier, org.mockito.Mockito.never()).sessionAssigned(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any());
    }

    /** 名额已满时不补位，避免超过工作台 3 个 Tab 的承载能力。 */
    @Test
    void onlineDoesNotPromoteWhenCapacityFull() {
        when(presenceRepository.findByStaffId(5L)).thenReturn(
                Optional.of(new AgentPresence(5L, PresenceStatus.BUSY, 3, 3, 0L)));
        when(clock.instant()).thenReturn(NOW);
        when(sessionRepository.countActiveByStaff(5L)).thenReturn(3);

        useCase.setPresence(new SetPresenceCmd(5L, PresenceStatus.ONLINE, 1L));

        verify(sessionRepository, org.mockito.Mockito.never()).findWaiting();
    }
}
