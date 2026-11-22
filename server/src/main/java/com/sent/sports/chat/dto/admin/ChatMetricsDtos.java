package com.sent.sports.chat.dto.admin;

import java.util.List;

/** 客服指标查询契约：一行一位客服，比率由服务层从原始计数派生。 */
public final class ChatMetricsDtos {

    private ChatMetricsDtos() {
    }

    /**
     * @param satisfactionRate 满意率百分比；无有效评价为 -1（前端显示 --）
     * @param resolutionRate   解决率百分比；无已结束会话为 -1（前端显示 --）
     * @param avgDurationSeconds 平均会话时长（秒，向下取整）
     * @param presence         坐席当前状态 ONLINE / BUSY / OFFLINE（无记录按 OFFLINE），供指标页实时展示与筛选
     */
    public record Row(long staffId, String staffName, int closedCount, int ratedCount,
                      int satisfiedCount, int satisfactionRate, int resolvedCount,
                      int resolutionRate, long avgDurationSeconds, String presence) {
    }

    /** @param from 统计起日 yyyy-MM-dd；@param to 统计止日 yyyy-MM-dd */
    public record Result(String from, String to, List<Row> rows) {
    }
}
