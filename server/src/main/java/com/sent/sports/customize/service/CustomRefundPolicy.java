package com.sent.sports.customize.service;

/**
 * 定制行退款处置档位（跨模块契约，2026-11-21 用户口径）。
 *
 * <p>之所以是三档而不是一个布尔，是因为「审核通过未排产（APPROVED）」既不属于
 * 「客户自己点就能退」，也不属于「一律不许退」——它必须由客服确认是否已备料/打样，
 * 布尔值表达不了这一档。refund 模块按档位分流：</p>
 * <ul>
 *   <li>{@link #SELF_SERVICE}：照常生成退款单，无需额外标记；</li>
 *   <li>{@link #MANUAL_REVIEW}：允许提交，但打上人工复核标记，交给客服在审核台处理；</li>
 *   <li>{@link #BLOCKED}：直接拒绝，抛业务异常；</li>
 *   <li>{@link #NOT_CUSTOM}：该订单行没有定制，不参与定制规则判定。</li>
 * </ul>
 *
 * <p>档位划分的唯一真源在此枚举的使用方 {@code CustomizeRefundGatewayImpl}，
 * 前端只做展示层预判，不得另起一套状态表。</p>
 */
public enum CustomRefundPolicy {

    /** 还没进机器：用户可自助申请退款。 */
    SELF_SERVICE("可自助退款"),

    /** 图纸已过审但未排产：允许申请，必须人工复核。 */
    MANUAL_REVIEW("可申请，需人工复核"),

    /** 已进入生产链路：绝对拦截。 */
    BLOCKED("已生产，不可退款"),

    /** 该订单行没有定制。 */
    NOT_CUSTOM("非定制行");

    private final String chineseName;

    CustomRefundPolicy(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getChineseName() {
        return chineseName;
    }

    /** 是否需要人工复核（APPROVED / ESCALATED 档）。 */
    public boolean needsManualReview() {
        return this == MANUAL_REVIEW;
    }

    /** 是否绝对不可退。 */
    public boolean isBlocked() {
        return this == BLOCKED;
    }
}
