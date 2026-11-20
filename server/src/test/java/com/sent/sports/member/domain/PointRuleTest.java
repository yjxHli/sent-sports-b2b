package com.sent.sports.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 积分换算规则（spec §6.4：1 元 = 1 积分）。发放与扣回必须互逆，所以换算只有这一处。
 */
class PointRuleTest {

    @ParameterizedTest(name = "{0} 分 → {1} 积分")
    @CsvSource({
            "0, 0",
            "99, 0",
            "100, 1",
            "199, 1",
            "24200, 242",
            "-500, 0"
    })
    @DisplayName("按分向下取整，负数与 0 记 0")
    void pointsForCentFloorsToYuan(long cent, long expected) {
        assertEquals(expected, PointRule.pointsForCent(cent));
    }

    @Test
    @DisplayName("同一笔金额：发放得到的积分 == 退款扣回的积分（互逆）")
    void grantAndRevokeUseSameRule() {
        long cent = 12_345L;
        long granted = PointRule.pointsForCent(cent);
        long revoked = PointRule.pointsForCent(cent);

        assertEquals(granted, revoked);
        assertEquals(123L, granted);
    }
}
