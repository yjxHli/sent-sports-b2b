package com.sent.sports.chat.domain;

/**
 * 客服服务评价档位。
 * <p>只有 {@link #SATISFIED} 计入满意率分子；NORMAL / UNSATISFIED 都算有效评价（分母）。
 */
public enum SatisfactionScore {
    SATISFIED("满意"),
    NORMAL("一般"),
    UNSATISFIED("不满意");

    private final String label;

    SatisfactionScore(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 严格解析；非法值抛 IllegalArgumentException，由用例转成 PARAM_INVALID。 */
    public static SatisfactionScore of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("score is blank");
        }
        return SatisfactionScore.valueOf(value.trim().toUpperCase());
    }
}
