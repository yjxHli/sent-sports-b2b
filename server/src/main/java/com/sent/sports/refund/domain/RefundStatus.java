package com.sent.sports.refund.domain;

/** 退款单状态（refund 模块独立维度）。 */
public enum RefundStatus {
    PENDING("待审核", false),
    REFUNDING("退款中", false),
    REFUNDED("已退款", false),
    FAILED("失败", false),
    CLOSED("已关闭", true);

    private final String chineseName;
    private final boolean terminal;

    RefundStatus(String chineseName, boolean terminal) {
        this.chineseName = chineseName;
        this.terminal = terminal;
    }

    public String getChineseName() {
        return chineseName;
    }

    public boolean isTerminal() {
        return terminal;
    }
}
