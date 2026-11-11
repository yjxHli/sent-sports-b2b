package com.sent.sports.chat.repository.impl;

import com.sent.sports.chat.domain.AgentPresence;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 坐席在线状态仓储（进程内适配器；接真库时映射 V9 的 staff_presence）。 */
@Repository
public class InMemoryAgentPresenceRepository implements AgentPresenceRepository {

    private final Map<Long, AgentPresence> byStaffId = new ConcurrentHashMap<>();

    @Override
    public void save(AgentPresence presence) {
        byStaffId.put(presence.getStaffId(), presence);
    }

    @Override
    public Optional<AgentPresence> findByStaffId(long staffId) {
        return Optional.ofNullable(byStaffId.get(staffId));
    }

    @Override
    public List<AgentPresence> findAll() {
        return List.copyOf(byStaffId.values());
    }
}
