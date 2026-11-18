package com.sent.sports.cart.service.dto;

import com.sent.sports.common.dto.TierDto;

import java.util.List;

/**
 * 试算单行指令：携带取价所需的中立参数（与 product.service.dto.PriceRequest 对齐）。
 *
 * @param customFlag 是否定制件：定制件按 10 元/件加收（2026-11-17 用户要求）
 */
public record QuoteItemCmd(
        long skuId,
        int qty,
        String customerTypeCode,
        int memberDiscountRate,
        int agreementDiscountRate,
        boolean allowStacking,
        List<TierDto> ladderTiers,
        boolean customFlag) {

    /** 兼容既有七参调用（普通商品）。 */
    public QuoteItemCmd(long skuId, int qty, String customerTypeCode,
                        int memberDiscountRate, int agreementDiscountRate,
                        boolean allowStacking, List<TierDto> ladderTiers) {
        this(skuId, qty, customerTypeCode, memberDiscountRate, agreementDiscountRate,
                allowStacking, ladderTiers, false);
    }
}
