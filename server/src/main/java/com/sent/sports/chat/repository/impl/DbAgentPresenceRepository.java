package com.sent.sports.chat.repository.impl;

import com.sent.sports.chat.domain.AgentPresence;
import com.sent.sports.chat.domain.PresenceStatus;
import com.sent.sports.chat.mapper.StaffPresenceMapper;
import com.sent.sports.chat.mapper.po.StaffPresencePO;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.common.util.TimeUtil;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 坐席在线状态仓储（真库，2026-11-28 批次 2 / P1-08）。
 *
 * <p>替换原先的 {@code InMemoryAgentPresenceRepository}：状态存在进程 Map 里时，
 * 应用重启即全部丢失、多实例各看各的，而指标页读的是 {@code staff_presence} 表（恒空）
 * → 所有坐席永远显示「离线」，{@code PresenceReclaimJob} 也永远更新 0 行。</p>
 *
 * <p><b>active_sessions 的口径</b>：这是「当前接待数」的尽力而为缓存，写的是绝对值。
 * 并发关单可能短暂少计，但下一次心跳（切状态/接单/关单）会用
 * {@code chat_session} 的真实 ACTIVE 数重置（{@code PresenceUseCase} 已如此实现），
 * 因此不会长期漂移；分配策略也以会话表为准做最终判定。</p>
 */
@Repository
public class DbAgentPresenceRepository implements AgentPresenceRepository {

    /** 表里没有记录时的默认并发上限：与 PresenceUseCase.DEFAULT_MAX_SESSIONS 对齐。 */
    private static final int DEFAULT_MAX_SESSIONS = 3;

    private final StaffPresenceMapper mapper;

    public DbAgentPresenceRepository(StaffPresenceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(AgentPresence presence) {
        mapper.upsert(presence.getStaffId(), presence.getPresence().name(),
                presence.getMaxSessions(), presence.getActiveSessions());
    }

    @Override
    public Optional<AgentPresence> findByStaffId(long staffId) {
        return Optional.ofNullable(mapper.selectByStaffId(staffId)).map(this::toDomain);
    }

    @Override
    public List<AgentPresence> findAll() {
        return mapper.selectAll().stream().map(this::toDomain).toList();
    }

    private AgentPresence toDomain(StaffPresencePO po) {
        return new AgentPresence(
                po.getStaffId() == null ? 0L : po.getStaffId(),
                parseStatus(po.getStatus()),
                po.getActiveSessions() == null ? 0 : po.getActiveSessions(),
                po.getMaxSessions() == null ? DEFAULT_MAX_SESSIONS : po.getMaxSessions(),
                TimeUtil.toMillis(po.getLastHeartbeatAt()));
    }

    /** 脏状态字符串（手工改库/历史数据）一律按离线处理，绝不让分配策略误以为有人在线。 */
    private static PresenceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return PresenceStatus.OFFLINE;
        }
        try {
            return PresenceStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PresenceStatus.OFFLINE;
        }
    }
}
