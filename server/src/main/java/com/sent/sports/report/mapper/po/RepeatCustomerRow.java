package com.sent.sports.report.mapper.po;

import java.time.LocalDateTime;

/** 单客户复购判定的只读投影：区间内订单数/金额 + 该客户历史首单时间。 */
public class RepeatCustomerRow {

    private Long customerId;
    private Integer periodOrderCount;
    private Long periodAmountCent;
    private LocalDateTime firstPaidAt;
    /** 历史有效订单总数（含窗口前），用于复购频次分档 */
    private Integer totalOrderCount;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Integer getPeriodOrderCount() {
        return periodOrderCount;
    }

    public void setPeriodOrderCount(Integer periodOrderCount) {
        this.periodOrderCount = periodOrderCount;
    }

    public Long getPeriodAmountCent() {
        return periodAmountCent;
    }

    public void setPeriodAmountCent(Long periodAmountCent) {
        this.periodAmountCent = periodAmountCent;
    }

    public LocalDateTime getFirstPaidAt() {
        return firstPaidAt;
    }

    public void setFirstPaidAt(LocalDateTime firstPaidAt) {
        this.firstPaidAt = firstPaidAt;
    }

    public Integer getTotalOrderCount() {
        return totalOrderCount;
    }

    public void setTotalOrderCount(Integer totalOrderCount) {
        this.totalOrderCount = totalOrderCount;
    }
}
