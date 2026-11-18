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

    /**
     * 单坐席（2026-11-16）：系统只用 9001 接待，客户会话统一分配给他。
     * 其他客服（9003 等）即使在线也不参与自动分配——保留 presence 表是为了
     * 历史报表/手动分配，但进线分配只认 9001。
     */
    public static final long SINGLE_AGENT_STAFF_ID = 9001L;

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
            return reuse(open.get());
        }

        long now = clock.instant().toEpochMilli();

        // 单坐席：只把 9001 纳入候选，其他客服在线也不自动接待；
        // 9001 离线/满负荷时 picked 为空 → 会话进 WAITING，排队/接管链路保持不变。
        List<StaffAssignmentPolicy.StaffPresence> presences = presenceRepository.findAll().stream()
                .filter(p -> p.getStaffId() == SINGLE_AGENT_STAFF_ID)
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
            // 分配成功即占用一个接待名额：负载不 +1 的话并发上限永远判不满，队伍会无限增长
            presenceRepository.findByStaffId(picked.get().staffId()).ifPresent(presence -> {
                presence.increaseActive();
                presenceRepository.save(presence);
            });
        } else {
            session = new ChatSession(sessionId, cmd.customerId(), cmd.vip(), ChatSessionStatus.WAITING,
                    null, now, now, 0);
        }
        sessionRepository.save(session);
        return new CreateSessionResult(sessionId, session.getStatus(), session.getStaffId(), false);
    }

    /**
     * 复用已有未结束会话。
     *
     * <p>单坐席纠正（2026-11-16 用户实测「商城发了你好，工作台进行中/排队都是 0，刷新也没有」）：
     * 会话若是 ACTIVE 且被<b>别的客服</b>（9003 等历史/测试遗留）占着，9001 的工作台列表按
     * {@code staff_id} 过滤，永远看不到这条会话，客户消息等于发进黑洞。单坐席模式下 9001 是唯一
     * 接待人，所以这里把会话交还给他，并同步两边的接待名额。</p>
     *
     * <p>9001 不在线时保持原样：改成他名下的「进行中」没有意义，等上线后由工作台接待/接管。</p>
     */
    private CreateSessionResult reuse(ChatSession session) {
        Long owner = session.getStaffId();
        boolean heldByOthers = session.getStatus() == ChatSessionStatus.ACTIVE
                && owner != null
                && owner != SINGLE_AGENT_STAFF_ID;
        if (!heldByOthers || !isSingleSeatOnline()) {
            return new CreateSessionResult(session.getId(), session.getStatus(), owner, true);
        }
        session.reassign(SINGLE_AGENT_STAFF_ID);
        sessionRepository.save(session);
        presenceRepository.findByStaffId(owner).ifPresent(presence -> {
            presence.decreaseActive();
            presenceRepository.save(presence);
        });
        presenceRepository.findByStaffId(SINGLE_AGENT_STAFF_ID).ifPresent(presence -> {
            presence.increaseActive();
            presenceRepository.save(presence);
        });
        return new CreateSessionResult(session.getId(), session.getStatus(), SINGLE_AGENT_STAFF_ID, true);
    }

    private boolean isSingleSeatOnline() {
        return presenceRepository.findByStaffId(SINGLE_AGENT_STAFF_ID)
                .map(presence -> presence.getPresence() == PresenceStatus.ONLINE)
                .orElse(false);
    }
}
