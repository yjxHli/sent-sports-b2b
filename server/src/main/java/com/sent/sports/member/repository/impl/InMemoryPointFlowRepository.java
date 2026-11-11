package com.sent.sports.member.repository.impl;

import com.sent.sports.member.domain.PointFlow;
import com.sent.sports.member.repository.PointFlowRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 积分流水（进程内适配器，保留全部变动便于调试；接真库时映射 V3 的 point_log）。 */
@Repository
public class InMemoryPointFlowRepository implements PointFlowRepository {

    private final List<PointFlow> flows = new CopyOnWriteArrayList<>();

    @Override
    public void save(PointFlow flow) {
        flows.add(flow);
    }

    public List<PointFlow> all() {
        return List.copyOf(flows);
    }
}
