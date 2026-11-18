package com.sent.sports.cart.service.dto;

import java.util.List;

/**
 * 结算试算指令：整单级参数（可用积分、运费规则、优惠券）+ 商品行集合。
 */
public record CartQuoteCmd(
        int availablePoints,
        int freightFreeThresholdCent,
        int freightFeeCent,
        List<QuoteItemCmd> items,
        Long couponId,
        long customerId) {

    /** 兼容既有四参调用（不使用优惠券）。 */
    public CartQuoteCmd(int availablePoints, int freightFreeThresholdCent, int freightFeeCent,
                        List<QuoteItemCmd> items) {
        this(availablePoints, freightFreeThresholdCent, freightFeeCent, items, null, 0L);
    }
}
