package com.sent.sports.member.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 会员聚合领域测试（P3-45/P3-47）：授级写入与到期回落、积分增减、成长值升级（只升不降）。
 */
class MemberTest {

    @Test
    void grantWritesLevelAndValidity() {
        Member m = new Member(1L, MemberLevel.NORMAL, 0L, 0L);
        m.grantLevel(MemberLevel.VIP1, 2000L, "达标");
        assertEquals(MemberLevel.VIP1, m.getLevel());
        assertEquals(2000L, m.getLevelValidToMillis());
        assertEquals("达标", m.getLastGrantReason());
    }

    @Test
    void expiredGrantRevertsToGrowthLevel() {
        Member m = new Member(1L, MemberLevel.NORMAL, 5000L, 0L); // 成长值 5000 → 本应 VIP1
        m.grantLevel(MemberLevel.VIP2, 1000L, "临时");
        assertEquals(MemberLevel.VIP2, m.getLevel());
        m.expireGrantIfNeeded(2000L); // 过期
        assertEquals(MemberLevel.VIP1, m.getLevel());
        assertEquals(0L, m.getLevelValidToMillis());
    }

    @Test
    void adjustPointsAllowsNegative() {
        Member m = new Member(1L, MemberLevel.NORMAL, 0L, 100L);
        m.adjustPoints(-30);
        assertEquals(70L, m.getAvailablePoints());
        m.adjustPoints(50);
        assertEquals(120L, m.getAvailablePoints());
    }

    @Test
    void addGrowthUpgradesButNeverDowngrades() {
        Member m = new Member(1L, MemberLevel.NORMAL, 0L, 0L);
        m.addGrowth(3000); // NORMAL→VIP1
        assertEquals(MemberLevel.VIP1, m.getLevel());
        m.addGrowth(-1000); // 成长值回落但等级不降
        assertEquals(MemberLevel.VIP1, m.getLevel());
    }
}
