package com.sent.sports.product.service.dto;

import com.sent.sports.common.dto.TierDto;

import java.util.List;

/**
 * 取价请求（中立 DTO）：用例层不依赖 product 领域类型，统一走 service 契约。
 */
public record PriceRequest(
        long skuId,
        int qty,
        String customerTypeCode,
        int memberDiscountRate,
        int agreementDiscountRate,
        boolean allowStacking,
        List<TierDto> ladderTiers) {
}
