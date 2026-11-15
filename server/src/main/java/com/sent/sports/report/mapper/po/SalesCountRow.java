package com.sent.sports.report.mapper.po;

/** 指定时间窗内的成交汇总只读投影（今日订单数 / 今日实付金额）。 */
public class SalesCountRow {

    private Long orderCount;
    private Long amountCent;

    public Long getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Long orderCount) {
        this.orderCount = orderCount;
    }

    public Long getAmountCent() {
        return amountCent;
    }

    public void setAmountCent(Long amountCent) {
        this.amountCent = amountCent;
    }
}
