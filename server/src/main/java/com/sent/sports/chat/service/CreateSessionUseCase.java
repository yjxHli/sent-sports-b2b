package com.sent.sports.chat.service;

import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.PresenceStatus;
import com.sent.sports.chat.domain.StaffAssignmentPolicy;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.dto.CreateSessionCmd;
import com.sent.sports.chat.service.dto.CreateSessionResult;
import com.sent.sports.common.anno.UseCase;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 发起会话用例（P3-50/51）：spec §6.10 / 验收 A17/A20/A22——
 * 已有进行中会话则复用；有可接待客服且未达并发上限则 ACTIVE，否则 WAITING 进排队。
 * 仅依赖 chat 模块内部仓储端口与 common，符合 ArchUnit 规则 4。
 */
@Service
public class CreateSessionUseCase {

    public static final int MAX_CONCURRENT = 1000;

    private final ChatSessionRepository sessionRepository;
    private final AgentPresenceRepository presenceRepository;
    private final Clock clock;

    public CreateSessionUseCase(ChatSessionRepository sessionRepository,
                                AgentPresenceRepository presenceRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.presenceRepository = presenceRepository;
        this.clock = clock;
    }

    @UseCase
    public CreateSessionResult create(CreateSessionCmd cmd) {
        List<ChatSession> existing = sessionRepository.findByCustomerId(cmd.customerId());
        // 复用口径：未结束（ACTIVE / WAITING）即复用，保证「一位客户同时只有一条会话」。
        Optional<ChatSession> open = ChatSession.findOpen(existing, cmd.customerId());
        if (open.isPresent()) {
            ChatSession session = open.get();
            return new CreateSessionResult(session.getId(), session.getStatus(), session.getStaffId(), true);
        }

        long now = clock.instant().toEpochMilli();

        List<StaffAssignmentPolicy.StaffPresence> presences = presenceRepository.findAll().stream()
                .map(p -> new StaffAssignmentPolicy.StaffPresence(
                        p.getStaffId(),
                        p.getPresence() == PresenceStatus.ONLINE,
                        p.getActiveSessions(),
                        p.getMaxSessions()))
                .toList();

        Optional<StaffAssignmentPolicy.StaffPresence> picked = StaffAssignmentPolicy.assign(presences);
        boolean underCap = sessionRepository.countActive() < MAX_CONCURRENT;

        long sessionId = sessionRepository.nextId();
        ChatSession session;
        if (picked.isPresent() && underCap) {
            session = new ChatSession(sessionId, cmd.customerId(), cmd.vip(), ChatSessionStatus.ACTIVE,
                    picked.get().staffId(), now, now, 0);
        } else {
            session = new ChatSession(sessionId, cmd.customerId(), cmd.vip(), ChatSessionStatus.WAITING,
                    null, now, now, 0);
        }
        sessionRepository.save(session);
        return new CreateSessionResult(sessionId, session.getStatus(), session.getStaffId(), false);
    }
}
