package com.sent.sports.member.repository;

import com.sent.sports.member.domain.PointFlow;

/**
 * 积分流水仓储端口（P3-47 引入，实现见 Phase 5）：持久化积分变动流水。
 */
public interface PointFlowRepository {
    void save(PointFlow flow);
}
