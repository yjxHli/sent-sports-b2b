package com.sent.sports.customize.domain;

import java.util.List;
import java.util.Map;

/**
 * 定制计费配置（spec §6.5.2）：每个面数的基础单价（分）+ 数量阶梯折扣。
 * 无颜色系数——彩色与单色同价。
 */
public class CustomFeeConfig {

    private final Map<FaceSide, Long> baseUnitCent;
    private final List<QtyTier> tiers;

    public CustomFeeConfig(Map<FaceSide, Long> baseUnitCent, List<QtyTier> tiers) {
        this.baseUnitCent = baseUnitCent;
        this.tiers = tiers;
    }

    public Map<FaceSide, Long> getBaseUnitCent() {
        return baseUnitCent;
    }

    public List<QtyTier> getTiers() {
        return tiers;
    }

    /** 命中数量阶梯的折扣率（百分比，100=不打折）；无命中返回 100。maxQty<0 表示无上限。 */
    public long tierRate(int qty) {
        for (QtyTier t : tiers) {
            if (qty >= t.minQty() && (t.maxQty() < 0 || qty <= t.maxQty())) {
                return t.rate();
            }
        }
        return 100;
    }

    /** 数量阶梯区间：minQty 含，maxQty 含；maxQty<0 表示无上限。rate 为百分比折扣。 */
    public record QtyTier(int minQty, int maxQty, int rate) {
    }
}
