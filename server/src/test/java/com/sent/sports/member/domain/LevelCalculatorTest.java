package com.sent.sports.member.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P2-33 等级计算测试（TDD 红）：spec §6.3.1。
 * 三档门槛 0 / 2000 / 10000 成长值 → NORMAL / VIP1 / VIP2。
 */
class LevelCalculatorTest {

    @Test
    void thresholds() {
        assertEquals(MemberLevel.NORMAL, LevelCalculator.calcLevel(0));
        assertEquals(MemberLevel.NORMAL, LevelCalculator.calcLevel(1999));
        assertEquals(MemberLevel.VIP1, LevelCalculator.calcLevel(2000));
        assertEquals(MemberLevel.VIP1, LevelCalculator.calcLevel(9999));
        assertEquals(MemberLevel.VIP2, LevelCalculator.calcLevel(10000));
        assertEquals(MemberLevel.VIP2, LevelCalculator.calcLevel(12345));
    }

    @Test
    void discountRates() {
        assertEquals(100, MemberLevel.NORMAL.getDiscountRate());
        assertEquals(98, MemberLevel.VIP1.getDiscountRate());
        assertEquals(95, MemberLevel.VIP2.getDiscountRate());
    }

    @Test
    void pointRates() {
        assertEquals(1.0d, MemberLevel.NORMAL.getPointRate(), 0.0001);
        assertEquals(1.0d, MemberLevel.VIP1.getPointRate(), 0.0001);
        assertEquals(1.2d, MemberLevel.VIP2.getPointRate(), 0.0001);
    }
}
