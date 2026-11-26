package com.sent.sports.report.mapper.po;

/** 客户类型聚合的只读投影（customer_type 为 null / 空时由 SQL 归 UNKNOWN）。 */
public class CustomerMixRow {

    private String customerType;
    private Long customerCount;
    private Long orderCount;
    private Long amountCent;

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public Long getCustomerCount() {
        return customerCount;
    }

    public void setCustomerCount(Long customerCount) {
        this.customerCount = customerCount;
    }

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
