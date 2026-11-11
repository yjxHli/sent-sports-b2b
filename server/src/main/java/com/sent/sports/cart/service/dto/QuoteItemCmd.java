package com.sent.sports.cart.service.dto;

import com.sent.sports.common.dto.TierDto;

import java.util.List;

/**
 * 试算单行指令：携带取价所需的中立参数（与 product.service.dto.PriceRequest 对齐）。
 */
public record QuoteItemCmd(
        long skuId,
        int qty,
        String customerTypeCode,
        int memberDiscountRate,
        int agreementDiscountRate,
        boolean allowStacking,
        List<TierDto> ladderTiers) {
}
