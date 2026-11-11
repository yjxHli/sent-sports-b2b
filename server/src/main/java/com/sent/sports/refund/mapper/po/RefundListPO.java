package com.sent.sports.refund.mapper.po;

import java.time.LocalDateTime;

/**
 * 用户端「我的售后」列表行投影（refund_order 左联 orders 取订单号）。
 * 只读 DTO，不参与写库，故不带 {@code @TableName}。
 */
public class RefundListPO {

    private String refundNo;
    private String orderNo;
    private String status;
    private Long amountCent;
    private LocalDateTime createdAt;

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAmountCent() {
        return amountCent;
    }

    public void setAmountCent(Long amountCent) {
        this.amountCent = amountCent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
