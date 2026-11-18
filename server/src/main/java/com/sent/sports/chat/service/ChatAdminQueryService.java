package com.sent.sports.chat.service;

import com.sent.sports.chat.dto.admin.ChatMetricsDtos;
import com.sent.sports.chat.service.dto.ChatAuditDtos;
import com.sent.sports.chat.service.dto.OpenSessionsResult;
import com.sent.sports.chat.service.dto.TodayStatsResult;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;

import java.time.LocalDateTime;
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

    /** 单会话状态（工作台 Tab 刷新：已结束会话不再出现在 openSessions 里）。 */
    ChatAuditDtos.SessionDetail sessionDetail(long sessionId);

    /** 会话回放：消息 + 客服填写的小结（历史服务与客服审查共用）。 */
    ChatAuditDtos.Transcript transcript(long sessionId);

    /** 同一客户最近 N 次已结束会话（服务端强制上限 10）。 */
    List<ChatAuditDtos.HistoryRow> customerHistory(long customerId, long excludeSessionId, int limit);

    /** 客服审查分页：列表与 count 同条件。 */
    PageResult<ChatAuditDtos.AuditRow> audit(PageQuery page, Long staffId, LocalDateTime from,
                                             LocalDateTime to, String result, String keyword);

    /** 审查页的客服下拉。 */
    List<ChatAuditDtos.AuditStaffRow> auditStaff();

    /**
     * 客服指标：按半开区间 [from, to) 聚合每位客服的接待量 / 满意率 / 解决率 / 平均时长。
     * from 或 to 为空时由实现给兜底区间，避免 SQL 出现悬空 AND。
     */
    ChatMetricsDtos.Result metrics(LocalDateTime from, LocalDateTime to, Long staffId);
}
