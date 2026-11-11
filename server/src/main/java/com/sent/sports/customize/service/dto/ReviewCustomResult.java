package com.sent.sports.customize.service.dto;

/**
 * 审核定制单结果（中立出参）：回执迁移后状态、锁定版本、审核时限与剩余毫秒。
 */
public record ReviewCustomResult(String customOrderId, String status, int lockedVersion,
                                long reviewDeadlineMillis, long remainingMillis) {
}
