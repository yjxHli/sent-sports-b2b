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

    @Test
    void createsActiveWhenStaffAvailable() {
        when(sessionRepository.findByCustomerId(7L)).thenReturn(List.of());
        when(presenceRepository.findAll()).thenReturn(List.of(
                new AgentPresence(5L, PresenceStatus.ONLINE, 0, 10, 0L)));
        when(sessionRepository.countActive()).thenReturn(0);
        when(sessionRepository.nextId()).thenReturn(100L);
        when(clock.instant()).thenReturn(NOW);

        CreateSessionResult r = useCase.create(new CreateSessionCmd(7L, false, 1L));

        verify(sessionRepository).save(sessionCaptor.capture());
        ChatSession saved = sessionCaptor.getValue();
        assertEquals(ChatSessionStatus.ACTIVE, saved.getStatus());
        assertEquals(5L, saved.getStaffId());
        assertEquals(100L, r.sessionId());
        assertEquals(ChatSessionStatus.ACTIVE, r.status());
        assertEquals(5L, r.staffId());
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
                new AgentPresence(5L, PresenceStatus.ONLINE, 0, 10, 0L)));
        when(sessionRepository.countActive()).thenReturn(CreateSessionUseCase.MAX_CONCURRENT);
        when(sessionRepository.nextId()).thenReturn(100L);
        when(clock.instant()).thenReturn(NOW);

        CreateSessionResult r = useCase.create(new CreateSessionCmd(7L, false, 1L));

        verify(sessionRepository).save(sessionCaptor.capture());
        assertEquals(ChatSessionStatus.WAITING, sessionCaptor.getValue().getStatus());
        assertEquals(ChatSessionStatus.WAITING, r.status());
    }
}
