package com.sent.sports.report.mapper.po;

/**
 * 大客户采购排行聚合的只读投影（无 {@code @TableName}，不写库）。
 *
 * <p>{@code customerName} 由 SQL 按「企业名称 → 昵称 → 客户#{id}」三层回落。</p>
 */
public class TopCustomerRow {

    private Long customerId;
    private String customerName;
    private String phone;
    private Integer orderCount;
    private Long amountCent;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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
