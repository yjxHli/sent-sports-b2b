package com.sent.sports.payment.domain;

/** 支付流水状态（payment 模块独立维度，与 order 的 PayStatus 解耦）。 */
public enum PaymentTxnStatus {
    CREATED("待支付", false),
    PAID("已支付", false),
    FAILED("支付失败", false),
    CLOSED("已关闭", true),
    REFUNDED("已退款", true);

    private final String chineseName;
    private final boolean terminal;

    PaymentTxnStatus(String chineseName, boolean terminal) {
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
