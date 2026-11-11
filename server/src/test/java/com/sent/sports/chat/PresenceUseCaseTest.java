package com.sent.sports.chat;

import com.sent.sports.chat.domain.AgentPresence;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.PresenceStatus;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
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
}
