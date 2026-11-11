package com.sent.sports.member.domain;

/**
 * 积分流水（member 模块，P3-47）：每一次积分变动的不可变记录。
 * 纯 Java 领域对象，无 Spring 依赖。
 */
public record PointFlow(String id, long customerId, long changePoints, long beforePoints,
                        long afterPoints, String bizType, String bizNo, long operatorId,
                        String remark, long createdAtMillis) {
}
