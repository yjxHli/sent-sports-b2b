package com.sent.sports.customize.service.dto;

/**
 * 排产/完工结果（中立出参）：回执迁移后状态与排产/完工时间戳。
 */
public record ProduceCustomResult(String customOrderId, String status, long startedAtMillis, long finishedAtMillis) {
}
