package com.sent.sports.cart.service.dto;

import java.util.List;

/**
 * 结算试算结果：逐行 + 商品总额 + 运费 + 积分抵扣 + 应付。
 */
public record CartQuoteResult(
        List<QuoteLine> lines,
        long goodsAmountCent,
        long freightCent,
        long pointDeductCent,
        long payableCent) {
}
