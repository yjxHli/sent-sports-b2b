package com.sent.sports.member.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 会员聚合领域测试（P3-45/P3-47）：授级写入与到期回落、积分增减、成长值升级（只升不降）。
 * R12 后等级类型统一为 MemberTier（门槛 0 / 1000 / 5000 / 20000）。
 */
class MemberTest {

    @Test
    void grantWritesLevelAndValidity() {
        Member m = new Member(1L, MemberTier.NORMAL, 0L, 0L);
        m.grantLevel(MemberTier.LV1, 2000L, "达标");
        assertEquals(MemberTier.LV1, m.getLevel());
        assertEquals(2000L, m.getLevelValidToMillis());
        assertEquals("达标", m.getLastGrantReason());
    }

    @Test
    void expiredGrantRevertsToGrowthLevel() {
        Member m = new Member(1L, MemberTier.NORMAL, 5000L, 0L); // 成长值 5000 → LV2
        m.grantLevel(MemberTier.LV3, 1000L, "临时");
        assertEquals(MemberTier.LV3, m.getLevel());
        m.expireGrantIfNeeded(2000L); // 过期
        assertEquals(MemberTier.LV2, m.getLevel());
        assertEquals(0L, m.getLevelValidToMillis());
    }

    @Test
    void adjustPointsAllowsNegative() {
        Member m = new Member(1L, MemberTier.NORMAL, 0L, 100L);
        m.adjustPoints(-30);
        assertEquals(70L, m.getAvailablePoints());
        m.adjustPoints(50);
        assertEquals(120L, m.getAvailablePoints());
    }

    @Test
    void addGrowthUpgradesButNeverDowngrades() {
        Member m = new Member(1L, MemberTier.NORMAL, 0L, 0L);
        m.addGrowth(3000); // NORMAL→LV1（门槛 1000）
        assertEquals(MemberTier.LV1, m.getLevel());
        m.addGrowth(-1000); // 成长值回落但等级不降
        assertEquals(MemberTier.LV1, m.getLevel());
    }
}
