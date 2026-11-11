package com.sent.sports.cart.service.dto;

/**
 * 试算单行结果：单价（分）、来源、是否低于成本、行小计。
 */
public record QuoteLine(
        long skuId,
        long unitPriceCent,
        String sourceCode,
        boolean belowCost,
        long lineTotalCent) {
}
