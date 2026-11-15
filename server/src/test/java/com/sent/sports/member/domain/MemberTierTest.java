package com.sent.sports.member.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 真库等级档位（TDD 红）：{@code customer.level_code} 的真实值域是 NORMAL / LV1 / LV2 / LV3，
 * 门槛 0 / 1000 / 5000 / 20000，与早期内存档位 {@link MemberLevel}（VIP1/VIP2）不是同一套。
 * <p>缺陷背景：成长值累加后从未回写 {@code level_code}，于是出现「成长值 16167 却是 LV1」，
 * 进度条按 LV1 的下一档 5000 计算直接顶满、差额算出 -11167。档位换算必须有唯一实现。</p>
 */
class MemberTierTest {

    @Test
    @DisplayName("按成长值取门槛 ≤ 成长值的最高档")
    void calcByGrowthThresholds() {
        assertEquals(MemberTier.NORMAL, MemberTier.calc(0L));
        assertEquals(MemberTier.NORMAL, MemberTier.calc(999L));
        assertEquals(MemberTier.LV1, MemberTier.calc(1000L));
        assertEquals(MemberTier.LV1, MemberTier.calc(4999L));
        assertEquals(MemberTier.LV2, MemberTier.calc(5000L));
        assertEquals(MemberTier.LV2, MemberTier.calc(16167L));
        assertEquals(MemberTier.LV3, MemberTier.calc(20000L));
        assertEquals(MemberTier.LV3, MemberTier.calc(999_999L));
    }

    @Test
    @DisplayName("等级码解析：大小写/空白归一，未知码回落 NORMAL 而不是抛异常")
    void parseCodeIsTolerant() {
        assertEquals(MemberTier.LV2, MemberTier.of(" lv2 "));
        assertEquals(MemberTier.LV3, MemberTier.of("LV3"));
        assertEquals(MemberTier.NORMAL, MemberTier.of(null));
        assertEquals(MemberTier.NORMAL, MemberTier.of("  "));
        assertEquals(MemberTier.NORMAL, MemberTier.of("SILVER"));
    }

    @Test
    @DisplayName("只升不降：max 永远取更高档，人工授级不会被成长值压低")
    void maxKeepsHigherTier() {
        assertEquals(MemberTier.LV2, MemberTier.max(MemberTier.LV1, MemberTier.LV2));
        assertEquals(MemberTier.LV2, MemberTier.max(MemberTier.LV2, MemberTier.NORMAL));
        assertEquals(MemberTier.LV3, MemberTier.max(MemberTier.LV3, MemberTier.LV1));
    }
}
