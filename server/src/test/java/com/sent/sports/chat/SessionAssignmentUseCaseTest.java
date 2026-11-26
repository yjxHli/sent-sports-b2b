package com.sent.sports.chat;

import com.sent.sports.chat.domain.AgentPresence;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.PresenceStatus;
import com.sent.sports.chat.domain.StaffAssignmentPolicy;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.ChatNotifier;
import com.sent.sports.chat.service.SessionAssignmentUseCase;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 转人工 / 排队会话的空位接入测试（2026-11-25，TDD 红）。
 *
 * <p>缺陷背景：商城端客服窗口一律先由智能助手接待，客户说的第一句话（如「你好」）如果触发转人工，
 * 会话从 BOT 切到 HUMAN + WAITING，但<b>不会再走一次自动分配</b>——只要坐席早已在线，
 * 「创建会话时分配」这条链路就再也不会被触发。表现为：9001 在线且名额全空，
 * 客户却在商城端看着「前方还有 0 人」的排队条干等，工作台也没有任何会话可以弹出来。
 * 旧版工作台把排队会话一起塞进自动开窗兜底，掩盖了这个缺口；排队栏删掉后它就暴露了。</p>
 */
class SessionAssignmentUseCaseTest extends BaseUseCaseTest {

    private static final long SINGLE_AGENT = StaffAssignmentPolicy.SINGLE_AGENT_STAFF_ID;

    @Mock
    private ChatSessionRepository sessionRepository;
    @Mock
    private AgentPresenceRepository presenceRepository;
    @Mock
    private ChatNotifier notifier;
    @InjectMocks
    private SessionAssignmentUseCase useCase;

    private ChatSession waitingHuman() {
        return new ChatSession(7L, 9002L, false, ChatSessionStatus.WAITING, null, 1L, 1L, 0);
    }

    private AgentPresence presence(PresenceStatus status, int active, int max) {
        return new AgentPresence(SINGLE_AGENT, status, active, max, 1L);
    }

    @Test
    @DisplayName("在线且有名额：排队会话直接接入，名额 +1，并推送「已接入」")
    void assignsWhenAgentOnlineAndHasCapacity() {
        ChatSession session = waitingHuman();
        AgentPresence agent = presence(PresenceStatus.ONLINE, 0, 3);
        when(presenceRepository.findAll()).thenReturn(List.of(agent));
        when(presenceRepository.findByStaffId(SINGLE_AGENT)).thenReturn(Optional.of(agent));

        assertTrue(useCase.assignIfCapacity(session));

        assertEquals(ChatSessionStatus.ACTIVE, session.getStatus());
        assertEquals(SINGLE_AGENT, session.getStaffId());
        assertEquals(1, agent.getActiveSessions(), "接入后必须占用一个名额，否则并发上限永远判不满");
        verify(sessionRepository).save(session);
        verify(presenceRepository).save(agent);
        verify(notifier).sessionAssigned(9002L, 7L, SINGLE_AGENT);
    }

    @Test
    @DisplayName("坐席离线：不动会话，也不推帧")
    void keepsWaitingWhenAgentOffline() {
        ChatSession session = waitingHuman();
        AgentPresence agent = presence(PresenceStatus.OFFLINE, 0, 3);
        when(presenceRepository.findAll()).thenReturn(List.of(agent));

        assertFalse(useCase.assignIfCapacity(session));

        assertEquals(ChatSessionStatus.WAITING, session.getStatus());
        assertNull(session.getStaffId());
        verify(sessionRepository, never()).save(any());
        verify(notifier, never()).sessionAssigned(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("名额已满：留在队列里，等关单补位")
    void keepsWaitingWhenCapacityIsExhausted() {
        ChatSession session = waitingHuman();
        AgentPresence agent = presence(PresenceStatus.ONLINE, 3, 3);
        when(presenceRepository.findAll()).thenReturn(List.of(agent));

        assertFalse(useCase.assignIfCapacity(session));

        assertEquals(ChatSessionStatus.WAITING, session.getStatus());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("非排队会话（已被接走/已结束）：幂等返回 false，不重复分配")
    void skipsNonWaitingSession() {
        ChatSession active = new ChatSession(7L, 9002L, ChatSessionStatus.ACTIVE, 9003L);

        assertFalse(useCase.assignIfCapacity(active));

        assertEquals(9003L, active.getStaffId());
        verify(presenceRepository, never()).findAll();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("指派给别的坐席的排队会话不抢")
    void ignoresSessionsPinnedToAnotherAgent() {
        // 早退分支不查 presence：断言这一点，避免以后有人把「不抢别人会话」的守卫挪到查询之后
        ChatSession pinned = new ChatSession(7L, 9002L, false, ChatSessionStatus.WAITING, 9003L, 1L, 1L, 0);

        assertFalse(useCase.assignIfCapacity(pinned));

        assertEquals(9003L, pinned.getStaffId(), "指派给别人的排队会话不能被抢");
        verify(presenceRepository, never()).findAll();
        verify(sessionRepository, never()).save(any());
    }
}
