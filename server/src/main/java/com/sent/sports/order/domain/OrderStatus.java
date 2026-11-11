package com.sent.sports.order.domain;

/** 订单主状态（单一维度，不与支付/定制/售后状态混用）。见 spec §6.6.1 / §15.1。 */
public enum OrderStatus {
    WAIT_PAY("待付款", false),
    WAIT_SHIP("待发货", false),
    PART_SHIPPED("部分发货", false),
    WAIT_RECEIVE("待收货", false),
    COMPLETED("已完成", false),
    CANCELLED("已取消", true),
    CLOSED("已关闭", true);

    private final String chineseName;
    private final boolean terminal;

    OrderStatus(String chineseName, boolean terminal) {
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
