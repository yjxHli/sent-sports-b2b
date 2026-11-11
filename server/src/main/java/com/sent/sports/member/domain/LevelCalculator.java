package com.sent.sports.member.domain;

/**
 * 等级计算（spec §6.3.1）：按累计成长值映射等级。
 * 取门槛 ≤ 成长值 中的最高档；不自动降级（降级仅退款扣回后次日重算）。
 */
public final class LevelCalculator {

    private LevelCalculator() {
    }

    public static MemberLevel calcLevel(long growthValue) {
        MemberLevel result = MemberLevel.NORMAL;
        for (MemberLevel level : MemberLevel.values()) {
            if (growthValue >= level.getThreshold()) {
                result = level;
            }
        }
        return result;
    }
}
