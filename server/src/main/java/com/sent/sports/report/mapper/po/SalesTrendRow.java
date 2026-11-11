package com.sent.sports.report.mapper.po;

/**
 * 销售趋势按日聚合的只读投影（无 {@code @TableName}，不写库）。
 * <p>{@code day} 由 {@code DATE_FORMAT(paid_at,'%Y-%m-%d')} 生成，避免时区转换误差。</p>
 */
public class SalesTrendRow {

    private String day;
    private Long amountCent;
    private Integer orderCount;

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public Long getAmountCent() {
        return amountCent;
    }

    public void setAmountCent(Long amountCent) {
        this.amountCent = amountCent;
    }

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }
}
