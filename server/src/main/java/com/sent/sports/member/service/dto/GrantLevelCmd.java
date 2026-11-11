package com.sent.sports.member.service.dto;

import com.sent.sports.member.domain.MemberLevel;

/**
 * 人工授级命令（中立入参）：客户、目标等级、有效期截止毫秒、原因、操作人。
 */
public record GrantLevelCmd(long customerId, MemberLevel targetLevel, long validToMillis, String reason, long operatorId) {
}
