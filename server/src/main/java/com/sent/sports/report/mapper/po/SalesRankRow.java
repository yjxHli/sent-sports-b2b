package com.sent.sports.report.mapper.po;

/**
 * 销售业绩排行聚合的只读投影（无 {@code @TableName}，不写库）。
 * <p>{@code staffName} 由 {@code LEFT JOIN sys_staff} 取得，归属为空时由 SQL 兜底「未分配」。</p>
 */
public class SalesRankRow {

    private Long staffId;
    private String staffName;
    private Integer customerCount;
    private Integer orderCount;
    private Long amountCent;

    public Long getStaffId() {
        return staffId;
    }

    public void setStaffId(Long staffId) {
        this.staffId = staffId;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }

    public Integer getCustomerCount() {
        return customerCount;
    }

    public void setCustomerCount(Integer customerCount) {
        this.customerCount = customerCount;
    }

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }

    public Long getAmountCent() {
        return amountCent;
    }

    public void setAmountCent(Long amountCent) {
        this.amountCent = amountCent;
    }
}
