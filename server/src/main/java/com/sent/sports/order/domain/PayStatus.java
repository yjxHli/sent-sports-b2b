package com.sent.sports.order.domain;

/** 支付状态（订单四独立维度之一，spec §6.6.2）。 */
public enum PayStatus {
    UNPAID("未支付", false),
    PAYING("支付中", false),
    PAID("已支付", false),
    PART_REFUNDED("部分退款", false),
    FULL_REFUNDED("全额退款", true);

    private final String chineseName;
    private final boolean terminal;

    PayStatus(String chineseName, boolean terminal) {
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
