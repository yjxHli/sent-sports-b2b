package com.sent.sports.order.service.dto;

import java.util.List;

/**
 * 订单退款视图（中立 DTO）：退款项列表 + 运费 + 是否已发货 + 买家。供 refund 模块消费，符合 ArchUnit 规则 4。
 */
public record OrderRefundView(String orderNo, long customerId, long orderId, List<RefundLineView> items, long shippingFeeCent, boolean shipped) {
}
