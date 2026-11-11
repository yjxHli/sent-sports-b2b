package com.sent.sports.stock.domain;

/**
 * 库存流水（stock 模块，P3-31）：每一次库存数量变动的不可变记录（plan §7.2 / spec §8.3）。
 * 类型见 {@code bizType}（LOCK/RELEASE/SHIP/INBOUND/LOSS/CHECK/REFUND_IN），台账可由流水重算。
 * 纯 Java 领域对象，无 Spring 依赖。
 */
public record StockFlow(String id, long skuId, String bizType, long changeQty,
                        long beforeQty, long afterQty, String bizNo, long operatorId,
                        String remark, long createdAtMillis) {
}
