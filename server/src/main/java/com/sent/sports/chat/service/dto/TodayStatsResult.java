package com.sent.sports.chat.service.dto;

/**
 * 工作台「今日完成接待数」（服务端按本地自然日统计，跨零点自动归零）。
 *
 * @param staffId     客服 ID
 * @param date        统计归属日期（yyyy-MM-dd，本地时区）
 * @param closedCount    当日已结束的会话数
 * @param ratedCount     当日有效评价数（一般/不满意都算有效）
 * @param satisfiedCount 当日「满意」评价数（满意率分子）
 * @param ratePercent    满意率百分比；无评价时为 -1，前端显示 `--`
 */
public record TodayStatsResult(long staffId, String date, int closedCount,
                               int ratedCount, int satisfiedCount, int ratePercent) {
}
