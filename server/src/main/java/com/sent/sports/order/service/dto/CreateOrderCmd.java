package com.sent.sports.order.service.dto;

import java.util.List;

/**
 * 创建订单指令：客户、收货地址、幂等键、可用积分、运费规则、商品行集合。
 *
 * <p>{@code addressId} 是<b>必填</b>：下单瞬间要把地址固化成快照（{@code orders.address_snapshot_json}），
 * 否则用户事后改地址簿会连带改掉历史订单的配送信息，发票 / 退款 / 物流全都对不上。</p>
 */
public record CreateOrderCmd(
        long customerId,
        Long addressId,
        String idemKey,
        int availablePoints,
        int freightFreeThresholdCent,
        int freightFeeCent,
        List<OrderLineCmd> items) {
}
