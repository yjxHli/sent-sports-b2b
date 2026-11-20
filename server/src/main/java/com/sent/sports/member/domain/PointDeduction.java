package com.sent.sports.member.domain;

/**
 * 积分抵扣计算（plan §7.1 / spec §6.4）：
 * <pre>
 *   抵扣分上限 = min(可用积分折分, 商品总额 × 30%)
 *   100 积分 = 1 元 = 100 分；单笔最多抵扣商品金额的 30%
 *   可用积分为负（过度消耗）时按 0 处理
 * </pre>
 */
public final class PointDeduction {

    private PointDeduction() {
    }

    /** 本次最多可抵扣金额（分）= min(可用积分折分, 商品总额 × 30%)。 */
    public static long maxDeductibleCent(int availablePoints, long goodsTotalCent) {
        long byPoints = Math.max(0L, (long) availablePoints); // 100 积分 = 100 分
        long cap = goodsTotalCent * 30L / 100L;               // 30% 上限
        return Math.min(byPoints, cap);
    }

    /** 抵扣后剩余积分（负积分保持为负）。 */
    public static long remainingPoints(int availablePoints, long goodsTotalCent) {
        return (long) availablePoints - maxDeductibleCent(availablePoints, goodsTotalCent);
    }

    /**
     * 抵扣金额（分）折算成要扣减的积分数：<b>1 积分 = 1 分</b>（100 积分 = 1 元）。
     *
     * <p>与发放侧的 {@link PointRule#pointsForCent}（1 元 = 1 积分）<b>相差 100 倍</b>：
     * 抵扣是「积分当钱花」，发放是「花钱攒积分」。支付成功扣积分时顺手调了发放侧的方法，
     * 用户抵 30 元只会被扣 0.3 积分——所以这个换算必须收在领域里、只能从这一处走。</p>
     */
    public static long pointsForDeductCent(long deductCent) {
        return Math.max(0L, deductCent);
    }
}
