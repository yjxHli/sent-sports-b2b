package com.sent.sports.member.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
