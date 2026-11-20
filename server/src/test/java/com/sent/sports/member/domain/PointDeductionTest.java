package com.sent.sports.member.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * P2-31 积分抵扣测试（TDD 红）：plan §7.1 / spec §6.4。
 * 抵扣 = min(可用积分折分, 商品总额 × 30%)；100 积分 = 1 元 = 100 分；
 * 余额不足受限；负积分按 0 处理。
 */
class PointDeductionTest {

    @Test
    void hundredPointsToOneYuanUnderCap() {
        // 商品额足够大不触顶：100 积分 → 100 分；10000 积分 → 10000 分
        assertEquals(100L, PointDeduction.maxDeductibleCent(100, 10_000_000L));
        assertEquals(10000L, PointDeduction.maxDeductibleCent(10000, 10_000_000L));
    }

    @Test
    void capAtThirtyPercentOfGoods() {
        // 商品 100.00 元 = 10000 分，30% = 3000 分；积分再多也只抵 3000
        assertEquals(3000L, PointDeduction.maxDeductibleCent(100000, 10000L));
    }

    @Test
    void insufficientBalanceLimitsByPoints() {
        assertEquals(50L, PointDeduction.maxDeductibleCent(50, 10_000_000L));
    }

    @Test
    void negativePointsDeductZero() {
        assertEquals(0L, PointDeduction.maxDeductibleCent(-100, 10_000_000L));
    }

    @Test
    void remainingPoints() {
        // 可用 10000，商品 10000 分（=100元）→ 实抵 3000，剩 7000
        assertEquals(7000L, PointDeduction.remainingPoints(10000, 10000L));
        // 负积分：实抵 0，余额保持为负
        assertEquals(-100L, PointDeduction.remainingPoints(-100, 10000L));
    }

    /**
     * 支付成功扣积分（P5 闭环）：抵扣金额（分）与要扣的积分数是 <b>1:1</b>——
     * 100 积分 = 1 元 = 100 分，所以 1 积分 = 1 分。
     *
     * <p>这个换算必须收在领域里：发放侧用的是 {@link PointRule#pointsForCent}（1 元 = 1 积分，
     * 即 100 分 = 1 积分），两者相差 100 倍。回调里要是顺手调了发放侧的方法，
     * 用户抵 30 元只被扣 0.3 积分，等于白送。</p>
     */
    @Test
    void pointsForDeductCentIsOnePointPerCent() {
        assertEquals(3000L, PointDeduction.pointsForDeductCent(3000L), "3000 分抵扣要扣 3000 积分");
        assertEquals(1L, PointDeduction.pointsForDeductCent(1L));
        assertEquals(0L, PointDeduction.pointsForDeductCent(0L), "没有抵扣就不扣积分");
        assertEquals(0L, PointDeduction.pointsForDeductCent(-3000L), "负数一律按 0，绝不反向发积分");
        // 与发放口径必须不同：同一个 3000 分，抵扣要 3000 积分，发放只记 30 积分
        assertNotEquals(PointRule.pointsForCent(3000L), PointDeduction.pointsForDeductCent(3000L));
    }

    /** 抵扣额与扣减积分必须成对可逆：maxDeductibleCent 给出的就是最终要扣的积分数。 */
    @Test
    void deductedPointsEqualCapAmount() {
        long cap = PointDeduction.maxDeductibleCent(50_000, 10_000L);   // 3000
        assertEquals(cap, PointDeduction.pointsForDeductCent(cap));
    }
}
