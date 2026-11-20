package com.sent.sports.member.domain;

import java.util.Locale;

/**
 * 真库等级档位（TDD 绿）：{@code customer.level_code} 的真实值域，门槛 0 / 1000 / 5000 / 20000。
 *
 * <p>全系统唯一的会员等级模型（R12）：早期内存档位 MemberLevel（0/2000/10000）已删除，
 * 规则（spec §6.3.1）统一在这里——取「门槛 ≤ 成长值」的最高档；
 * 等级只升不降，唯一回落路径是人工授级到期（{@code level_valid_to} 过期后按成长值重算）。</p>
 *
 * <p>放在 domain 包内，是纯 POJO 级逻辑（无 Spring 依赖），member 模块的写入链路
 * （{@code MemberRewardServiceImpl}）与读面（{@code MemberQueryServiceImpl}）共用同一份门槛，
 * 不再各自维护一份常量表导致口径漂移。</p>
 */
public enum MemberTier {

    NORMAL("NORMAL", 0L),
    LV1("LV1", 1000L),
    LV2("LV2", 5000L),
    LV3("LV3", 20000L);

    private final String code;
    private final long threshold;

    MemberTier(String code, long threshold) {
        this.code = code;
        this.threshold = threshold;
    }

    public String getCode() {
        return code;
    }

    public long getThreshold() {
        return threshold;
    }

    /** 按累计成长值取档：门槛 ≤ 成长值 中的最高档。 */
    public static MemberTier calc(long growthValue) {
        MemberTier result = NORMAL;
        for (MemberTier tier : values()) {
            if (growthValue >= tier.threshold) {
                result = tier;
            }
        }
        return result;
    }

    /** 等级码解析：大小写/空白归一，未知或空值回落 NORMAL（历史脏数据不抛异常）。 */
    public static MemberTier of(String code) {
        if (code == null || code.isBlank()) {
            return NORMAL;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (MemberTier tier : values()) {
            if (tier.code.equals(normalized)) {
                return tier;
            }
        }
        return NORMAL;
    }

    /** 只升不降：取两档中更高的一档（人工授级不会被成长值压低）。 */
    public static MemberTier max(MemberTier left, MemberTier right) {
        MemberTier a = left == null ? NORMAL : left;
        MemberTier b = right == null ? NORMAL : right;
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    /** 下一档；已是最高档时返回 null。 */
    public MemberTier next() {
        int nextIndex = ordinal() + 1;
        MemberTier[] all = values();
        return nextIndex < all.length ? all[nextIndex] : null;
    }
}
