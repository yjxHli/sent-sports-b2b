package com.sent.sports.cart.service.dto;

import java.util.List;

/**
 * 结算试算指令：整单级参数（可用积分、运费规则）+ 商品行集合。
 */
public record CartQuoteCmd(
        int availablePoints,
        int freightFreeThresholdCent,
        int freightFeeCent,
        List<QuoteItemCmd> items) {
}
