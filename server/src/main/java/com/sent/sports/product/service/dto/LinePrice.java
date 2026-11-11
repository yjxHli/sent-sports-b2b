package com.sent.sports.product.service.dto;

/**
 * 取价结果（中立 DTO）：单价（分）、取价来源码、是否低于成本。
 */
public record LinePrice(long unitPriceCent, String sourceCode, boolean belowCost) {
}
