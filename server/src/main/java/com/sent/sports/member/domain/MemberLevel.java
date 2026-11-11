package com.sent.sports.member.domain;

/**
 * 会员等级（spec §6.3.1）：三档，门槛与折扣率固定，权益随等级提升。
 * 等级只升不降（退款扣回成长值后次日按当前成长值重算，是唯一回落情形）。
 */
public enum MemberLevel {

    NORMAL(0, 100, 1.0d),
    VIP1(2000, 98, 1.0d),
    VIP2(10000, 95, 1.2d);

    private final long threshold;     // 累计成长值门槛
    private final int discountRate;   // 折扣率（百分比，100=无折扣）
    private final double pointRate;   // 积分倍率

    MemberLevel(long threshold, int discountRate, double pointRate) {
        this.threshold = threshold;
        this.discountRate = discountRate;
        this.pointRate = pointRate;
    }

    public long getThreshold() {
        return threshold;
    }

    public int getDiscountRate() {
        return discountRate;
    }

    public double getPointRate() {
        return pointRate;
    }
}
