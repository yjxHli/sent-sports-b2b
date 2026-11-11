package com.sent.sports.order.service.dto;

import java.util.List;

/**
 * 创建订单指令：客户、幂等键、可用积分、运费规则、商品行集合。
 */
public record CreateOrderCmd(
        long customerId,
        String idemKey,
        int availablePoints,
        int freightFreeThresholdCent,
        int freightFeeCent,
        List<OrderLineCmd> items) {
}
