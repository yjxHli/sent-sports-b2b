package com.sent.sports.customize.domain;

/**
 * 定制计费结果：单价（分）、数量、服务费总额（分）。
 */
public class CustomFeeResult {

    private final long unitPriceCent;
    private final int qty;
    private final long totalFeeCent;

    public CustomFeeResult(long unitPriceCent, int qty, long totalFeeCent) {
        this.unitPriceCent = unitPriceCent;
        this.qty = qty;
        this.totalFeeCent = totalFeeCent;
    }

    public long getUnitPriceCent() {
        return unitPriceCent;
    }

    public int getQty() {
        return qty;
    }

    public long getTotalFeeCent() {
        return totalFeeCent;
    }
}
