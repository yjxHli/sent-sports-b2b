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
}
