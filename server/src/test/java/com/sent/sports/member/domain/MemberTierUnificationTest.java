package com.sent.sports.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * R12（唯一等级模型）红→绿用例：会员聚合与全链路只允许 MemberTier，
 * 早先的 MemberLevel / LevelCalculator 门槛表（0/2000/10000）必须消失。
 */
class MemberTierUnificationTest {

    @Test
    @DisplayName("会员聚合统一用 MemberTier：成长值按 0/1000/5000/20000 取档")
    void memberUsesMemberTierThresholds() {
        Member member = new Member(1L, MemberTier.NORMAL, 0L, 0L);

        member.addGrowth(3000); // 旧表 VIP1 门槛 2000，新表 LV1 门槛 1000

        assertEquals(MemberTier.LV1, member.getLevel());
    }

    @Test
    @DisplayName("授级到期回落到成长值对应档（16167 → LV2，不是旧表的 VIP2）")
    void expiredGrantFallsBackToTierCalc() {
        Member member = new Member(1L, MemberTier.NORMAL, 16_167L, 0L);
        member.grantLevel(MemberTier.LV3, 1_000L, "临时授级");

        member.expireGrantIfNeeded(2_000L);

        assertEquals(MemberTier.LV2, member.getLevel());
    }
}
