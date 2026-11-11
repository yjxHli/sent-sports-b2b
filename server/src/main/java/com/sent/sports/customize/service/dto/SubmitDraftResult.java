package com.sent.sports.customize.service.dto;

/**
 * 提交图稿结果（中立出参）：回执新版本号、迁移后状态与审核截止时间。
 */
public record SubmitDraftResult(String customOrderId, int version, String status, long reviewDeadlineMillis) {
}
