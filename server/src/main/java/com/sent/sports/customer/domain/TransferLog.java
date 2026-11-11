package com.sent.sports.customer.domain;

/**
 * 客户归属流转日志（customer 模块，P3-49）：记录每一次改派的来龙去脉，留痕审计。
 * 纯 Java 领域对象，无 Spring 依赖。
 */
public record TransferLog(String id, long customerId, long fromStaffId, long toStaffId,
                         String reason, long operatorId, long createdAtMillis) {
}
