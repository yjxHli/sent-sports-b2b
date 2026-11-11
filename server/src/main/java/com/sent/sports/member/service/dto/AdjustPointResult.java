package com.sent.sports.member.service.dto;

/**
 * 积分调整结果（中立出参）：回执客户、调整前/后积分、增减额。
 */
public record AdjustPointResult(long customerId, long beforePoints, long afterPoints, long delta) {
}
