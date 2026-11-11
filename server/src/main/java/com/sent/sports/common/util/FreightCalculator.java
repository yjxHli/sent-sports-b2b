package com.sent.sports.common.util;

/**
 * 运费计算（spec O-01，中立工具）：满阈值包邮，否则收固定运费；空购物车（0 元）不计运费。
 * 置于 common.util 供 cart/order 复用，避免跨模块依赖对方 domain（ArchUnit 规则 4）。
 */
public final class FreightCalculator {

    private FreightCalculator() {
    }

    public static long calcFreight(long goodsAmountCent, int freeThresholdCent, int feeCent) {
        if (goodsAmountCent <= 0) {
            return 0;
        }
        if (goodsAmountCent >= freeThresholdCent) {
            return 0;
        }
        return feeCent;
    }
}
