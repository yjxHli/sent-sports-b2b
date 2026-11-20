package com.sent.sports.member.domain;

/**
 * 积分规则（spec §6.4）：获取与扣回同口径 —— <b>1 元 = 1 积分</b>（运费不计由调用方保证）。
 *
 * <p>为什么单独收成一个领域类：发放（订单完成）与扣回（退款成功）必须互逆，
 * 两处各写一遍 {@code / 100} 就是下一次「退款扣多了 / 发少了」的来源。</p>
 *
 * <p>纯 Java、零 Spring 依赖，可被单测直接钉住。</p>
 */
public final class PointRule {

    /** 1 元 = 100 分 → 每 100 分记 1 积分。 */
    public static final long CENT_PER_POINT = 100L;

    private PointRule() {
    }

    /** 金额（分）折算积分：向下取整，负数与 0 一律记 0（退款扣回由调用方取负号）。 */
    public static long pointsForCent(long amountCent) {
        return amountCent <= 0L ? 0L : amountCent / CENT_PER_POINT;
    }
}
