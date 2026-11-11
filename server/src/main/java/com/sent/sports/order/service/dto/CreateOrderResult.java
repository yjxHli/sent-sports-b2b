package com.sent.sports.order.service.dto;

/**
 * 创建订单结果：订单号、状态、应付、商品额、运费、积分抵扣。
 */
public record CreateOrderResult(
        String orderNo,
        String status,
        long payableCent,
        long goodsAmountCent,
        long freightCent,
        long pointDeductCent) {
}
