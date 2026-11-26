package com.sent.sports.chat.service;

import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.domain.PresenceStatus;
import com.sent.sports.chat.domain.StaffAssignmentPolicy;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.common.anno.UseCase;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 排队会话的空位接入（2026-11-25）——转人工后立刻找坐席，不必等客服手动点「接待」。
 *
 * <p>为什么需要它：商城端客服窗口一律先由智能助手接待（建会话即 BOT），
 * 客户说的第一句话一旦触发转人工，会话就从 BOT 切到 HUMAN + WAITING。
 * 而「分配坐席」原本只在 {@link CreateSessionUseCase} 建会话那一刻发生，
 * 于是<b>坐席早已在线</b>也永远不会再被分配：客户在商城端看着「前方还有 0 人」干等，
 * 工作台「进行中」空着，客服不点接待就没人接。</p>
 *
 * <p>口径与建会话、上线补位完全一致：只认单坐席 9001，在线且未达并发上限才接；
 * 接了就占一个名额并推 {@code sessionAssigned}（用户端据此隐藏排队条、解锁输入框）。
 * 离线/忙碌/名额满一律原地不动，交给上线补位与关单补位。</p>
 */
@Service
public class SessionAssignmentUseCase {

    private final ChatSessionRepository sessionRepository;
    private final AgentPresenceRepository presenceRepository;
    private final ChatNotifier notifier;

    public SessionAssignmentUseCase(ChatSessionRepository sessionRepository,
                                    AgentPresenceRepository presenceRepository,
                                    ChatNotifier notifier) {
        this.sessionRepository = sessionRepository;
        this.presenceRepository = presenceRepository;
        this.notifier = notifier;
    }

    /**
     * 排队会话若已有可接待坐席，立即接入。
     *
     * @return 是否接入成功；非 WAITING 会话（已被接走/已结束）幂等返回 false，不重复分配
     */
    @UseCase
    public boolean assignIfCapacity(ChatSession session) {
        if (session == null || session.getStatus() != ChatSessionStatus.WAITING) {
            return false;
        }
        // 指派给别人的排队会话不抢——与上线补位同一口径
        Long owner = session.getStaffId();
        if (owner != null && owner != StaffAssignmentPolicy.SINGLE_AGENT_STAFF_ID) {
            return false;
        }
        Optional<StaffAssignmentPolicy.StaffPresence> picked =
                StaffAssignmentPolicy.assign(candidates(StaffAssignmentPolicy.SINGLE_AGENT_STAFF_ID));
        if (picked.isEmpty()) {
            return false;
        }
        long staffId = picked.get().staffId();
        session.assign(staffId);
        sessionRepository.save(session);
        // 接了就占名额：不 +1 的话并发上限永远判不满，队伍会无限增长
        presenceRepository.findByStaffId(staffId).ifPresent(presence -> {
            presence.increaseActive();
            presenceRepository.save(presence);
        });
        notifier.sessionAssigned(session.getCustomerId(), session.getId(), staffId);
        return true;
    }

    /** 单坐席候选快照：其它客服在线也不参与自动分配，与进线建会话保持同一口径。 */
    private List<StaffAssignmentPolicy.StaffPresence> candidates(long staffId) {
        return presenceRepository.findAll().stream()
                .filter(p -> p.getStaffId() == staffId)
                .map(p -> new StaffAssignmentPolicy.StaffPresence(
                        p.getStaffId(),
                        p.getPresence() == PresenceStatus.ONLINE,
                        p.getActiveSessions(),
                        p.getMaxSessions()))
                .toList();
    }
}
