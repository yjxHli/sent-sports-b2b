package com.sent.sports.member.service.dto;

import com.sent.sports.member.domain.MemberLevel;

/**
 * 人工授级结果（中立出参）：回执客户、当前等级、有效期截止毫秒。
 */
public record GrantLevelResult(long customerId, MemberLevel level, long validToMillis) {
}
