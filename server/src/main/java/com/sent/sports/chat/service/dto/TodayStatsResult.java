package com.sent.sports.chat.service.dto;

/**
 * 工作台「今日完成接待数」（服务端按本地自然日统计，跨零点自动归零）。
 *
 * @param staffId     客服 ID
 * @param date        统计归属日期（yyyy-MM-dd，本地时区）
 * @param closedCount 当日已结束的会话数
 */
public record TodayStatsResult(long staffId, String date, int closedCount) {
}
