package com.sent.sports.cart.service.dto;

import java.util.List;

/** 结算试算结果：逐行 + 商品额 + 定制费 + 运费 + 积分抵扣 + 券抵扣 + 应付。 */
public record CartQuoteResult(
        List<QuoteLine> lines,
        long goodsAmountCent,
        long freightCent,
        long pointDeductCent,
        long payableCent,
        long customFeeCent,
        long couponDiscountCent) {

    /** 兼容既有五参调用（无定制件、无券）。 */
    public CartQuoteResult(List<QuoteLine> lines, long goodsAmountCent, long freightCent,
                           long pointDeductCent, long payableCent) {
        this(lines, goodsAmountCent, freightCent, pointDeductCent, payableCent, 0L, 0L);
    }

    /** 兼容既有六参调用（无券）。 */
    public CartQuoteResult(List<QuoteLine> lines, long goodsAmountCent, long freightCent,
                           long pointDeductCent, long payableCent, long customFeeCent) {
        this(lines, goodsAmountCent, freightCent, pointDeductCent, payableCent, customFeeCent, 0L);
    }
}
