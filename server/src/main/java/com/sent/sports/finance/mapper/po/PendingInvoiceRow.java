package com.sent.sports.finance.mapper.po;

import java.time.LocalDateTime;

/**
 * 「我的发票 — 未申请」行：可开票但还没有有效开票申请的订单。
 * <p>可开票口径与 {@code UserInvoiceServiceImpl} 一致：订单已完成且支付状态仍为 PAID / PART_REFUNDED；
 * 已存在 APPLIED / ISSUED 申请的订单不再出现，避免列表里重复引导开票。</p>
 */
public class PendingInvoiceRow {

    private String orderNo;
    private Long amountCent;
    private LocalDateTime completedAt;
    private String firstTitle;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getAmountCent() {
        return amountCent;
    }

    public void setAmountCent(Long amountCent) {
        this.amountCent = amountCent;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getFirstTitle() {
        return firstTitle;
    }

    public void setFirstTitle(String firstTitle) {
        this.firstTitle = firstTitle;
    }
}
