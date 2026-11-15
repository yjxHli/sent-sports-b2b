package com.sent.sports.chat.service;

import com.sent.sports.chat.service.dto.OpenSessionsResult;
import com.sent.sports.chat.service.dto.TodayStatsResult;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;

import java.util.List;

/**
 * 客服管理查询门面（P4-50 接口契约，跨模块可用）。
 * <p>会话/客服实体与仓储尚未建设（属 P2 领域 / P5 基础设施），当前由
 * {@code ChatAdminQueryServiceImpl} 桩实现占位；Phase 5 接入实体与数据范围（@DataScope）后替换。
 * 契约（接口签名）保持稳定。</p>
 */
public interface ChatAdminQueryService {

    /** 会话列表：待接入/进行中/已结束三态 + 分页。 */
    PageResult<?> sessions(PageQuery pageQuery);

    /** 客服工作量统计（在线状态 + 当前负载 + 并发上限）。 */
    List<?> workload();

    /**
     * 工作台待处理会话：某客服「进行中」+ 全员「排队中」（带排队位置）。
     * <p>工作台左侧两栏直接用这份数据，避免前端用分页接口再切片。
     */
    OpenSessionsResult openSessions(long staffId);

    /**
     * 今日完成接待数：{@code chat_session.status='CLOSED' AND staff_id=? AND ended_at ∈ 今天}。
     * <p>按本地自然日半开区间统计，第二天自然归零，不需要定时任务。
     */
    TodayStatsResult todayStats(long staffId);
}
