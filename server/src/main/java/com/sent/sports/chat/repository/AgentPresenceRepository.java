package com.sent.sports.chat.repository;

import com.sent.sports.chat.domain.AgentPresence;

import java.util.List;
import java.util.Optional;

/**
 * 客服在线状态仓储端口（plan §7.8）：实现见 Phase 5。
 */
public interface AgentPresenceRepository {

    /** 持久化在线状态。 */
    void save(AgentPresence presence);

    /** 按客服主键加载（无记录返回 empty）。 */
    Optional<AgentPresence> findByStaffId(long staffId);

    /** 全部客服在线状态快照（用于分配策略）。 */
    List<AgentPresence> findAll();
}
