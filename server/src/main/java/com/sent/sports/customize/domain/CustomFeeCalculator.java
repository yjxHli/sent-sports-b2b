package com.sent.sports.customize.domain;

/**
 * 定制服务费计算（spec §6.5.2 定稿）：
 * <pre>
 *   服务费 = 单价 × 数量
 *   单价   = 面数基础单价 × 数量阶梯折扣率（/100）
 * </pre>
 * 无颜色系数，避免前端试算与后端结算不一致。
 */
public final class CustomFeeCalculator {

    private CustomFeeCalculator() {
    }

    public static CustomFeeResult calc(CustomFeeConfig config, FaceSide face, int qty) {
        Long base = config.getBaseUnitCent().get(face);
        long unit = (base == null ? 0L : base) * config.tierRate(qty) / 100L;
        long total = unit * qty;
        return new CustomFeeResult(unit, qty, total);
    }
}
