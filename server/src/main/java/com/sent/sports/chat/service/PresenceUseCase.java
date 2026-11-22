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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 在线状态切换用例（P3-58/59）：spec §6.10——
 * ONLINE/BUSY/OFFLINE；离线时将其 ACTIVE 会话释放回 WAITING 队列(清空接待客服)。
 *
 * <p>2026-11-21 用户补充口径：
 * <ul>
 *   <li>登录后默认离线，切到「在线」才开始接人；</li>
 *   <li>「忙碌」= 暂停接待，排队客户不自动接入，切回在线才补位；</li>
 *   <li>因此切到 ONLINE 时要把队列里的客户按名额自动拉进来（此前只有关单补位，
 *       忙碌→在线没有出口，队列会一直干等）。</li>
 * </ul>
 * 仅依赖 chat 模块内部仓储端口与 common，符合 ArchUnit 规则 4。
 */
@Service
public class PresenceUseCase {

    /** 单客服默认并发接待上限：与工作台同时最多打开 3 个会话 Tab 对齐。 */
    static final int DEFAULT_MAX_SESSIONS = 3;

    private final AgentPresenceRepository presenceRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatNotifier notifier;
    private final Clock clock;

    public PresenceUseCase(AgentPresenceRepository presenceRepository,
                           ChatSessionRepository sessionRepository,
                           ChatNotifier notifier,
                           Clock clock) {
        this.presenceRepository = presenceRepository;
        this.sessionRepository = sessionRepository;
        this.notifier = notifier;
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
        if (cmd.presence() == PresenceStatus.ONLINE) {
            promoteWaiting(presence);
        }
        return new PresenceResult(cmd.staffId(), cmd.presence(), released);
    }

    /**
     * 上线即补位：按剩余名额把排队客户拉进接待（VIP 优先、同优先级先来后到）。
     *
     * <p>没有这一步，客服「忙碌时排了 3 个人 → 切回在线」队列不会动，
     * 客户得等客服手动点接待，与用户口径「切回在线才行」不符。
     * BUSY/OFFLINE 一律不补位。</p>
     */
    private void promoteWaiting(AgentPresence presence) {
        int capacity = presence.getMaxSessions() - presence.getActiveSessions();
        for (int i = 0; i < capacity; i++) {
            Optional<ChatSession> picked = sessionRepository.findWaiting().stream()
                    // 指派给别人的排队会话不抢；未指定分配对象（常态）或本就指向本坐席的才可接
                    .filter(s -> s.getStaffId() == null || s.getStaffId() == presence.getStaffId())
                    .min(Comparator.comparingLong(ChatSession::getCreatedAtMillis)
                            .thenComparingLong(ChatSession::getId));
            if (picked.isEmpty()) {
                return;
            }
            ChatSession next = picked.get();
            next.assign(presence.getStaffId());
            sessionRepository.save(next);
            presence.increaseActive();
            presenceRepository.save(presence);
            // 用户端据此隐藏排队提示、解锁输入框
            notifier.sessionAssigned(next.getCustomerId(), next.getId(), presence.getStaffId());
        }
    }
}
