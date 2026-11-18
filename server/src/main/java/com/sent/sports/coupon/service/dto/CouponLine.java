package com.sent.sports.coupon.service.dto;

/**
 * 券范围匹配所需的订单行（跨模块中立 DTO）。
 * <p>购物车与下单只传「SKU + 行金额」，不把商品领域对象带进券模块；
 * 券模块需要 SPU 时经 {@code ProductQueryService} 反查，符合 ArchUnit 规则 4。</p>
 */
public record CouponLine(long skuId, long amountCent) {
}
