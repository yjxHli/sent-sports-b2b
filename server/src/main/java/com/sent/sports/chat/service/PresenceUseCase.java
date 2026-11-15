package com.sent.sports.chat.service;

import com.sent.sports.chat.domain.AgentPresence;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.PresenceStatus;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.dto.PresenceResult;
import com.sent.sports.chat.service.dto.SetPresenceCmd;
import com.sent.sports.common.anno.UseCase;

import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 在线状态切换用例（P3-58/59）：spec §6.10——
 * ONLINE/BUSY/OFFLINE；离线时将其 ACTIVE 会话释放回 WAITING 队列(清空接待客服)。
 * 重分配由 AssignSessionUseCase 从队列拉取，本用例仅负责释放。
 * 仅依赖 chat 模块内部仓储端口与 common，符合 ArchUnit 规则 4。
 */
@Service
public class PresenceUseCase {

    /** 单客服默认并发接待上限：与工作台同时最多打开 3 个会话 Tab 对齐。 */
    static final int DEFAULT_MAX_SESSIONS = 3;

    private final AgentPresenceRepository presenceRepository;
    private final ChatSessionRepository sessionRepository;
    private final Clock clock;

    public PresenceUseCase(AgentPresenceRepository presenceRepository,
                           ChatSessionRepository sessionRepository, Clock clock) {
        this.presenceRepository = presenceRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    @UseCase
    public PresenceResult setPresence(SetPresenceCmd cmd) {
        long now = clock.instant().toEpochMilli();
        AgentPresence presence = presenceRepository.findByStaffId(cmd.staffId())
                .orElseGet(() -> new AgentPresence(cmd.staffId(), PresenceStatus.OFFLINE,
                        0, DEFAULT_MAX_SESSIONS, 0L));
        presence.setPresence(cmd.presence());
        presence.setUpdatedAtMillis(now);

        int released = 0;
        if (cmd.presence() == PresenceStatus.OFFLINE) {
            List<ChatSession> active = sessionRepository.findActiveByStaff(cmd.staffId());
            for (ChatSession session : active) {
                session.release();
                sessionRepository.save(session);
                released++;
            }
            // 全部释放回队列，名额归零
            presence.resetActive(0);
        } else {
            // 上线/忙碌：负载以库里真实 ACTIVE 会话数为准，避免进程重启或异常退出后负载漂移
            presence.resetActive(sessionRepository.countActiveByStaff(cmd.staffId()));
        }
        presenceRepository.save(presence);
        return new PresenceResult(cmd.staffId(), cmd.presence(), released);
    }
}
