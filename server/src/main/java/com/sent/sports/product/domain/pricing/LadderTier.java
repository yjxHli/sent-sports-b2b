package com.sent.sports.product.domain.pricing;

/**
 * 阶梯价档位：命中区间 [minQty, maxQty]（maxQty < 0 表示无上限）。
 * rate 为折扣率百分比（100=不打折，90=9 折）。
 */
public record LadderTier(int minQty, int maxQty, int rate) {

    public boolean matches(int qty) {
        return qty >= minQty && (maxQty < 0 || qty <= maxQty);
    }

    @Override
    public String toString() {
        return "L" + minQty + (maxQty < 0 ? "+" : "-" + maxQty) + "@" + rate;
    }
}
