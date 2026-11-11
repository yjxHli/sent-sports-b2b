package com.sent.sports.refund.mapper.po;

import java.time.LocalDateTime;

/**
 * 用户端售后详情投影（refund_order 左联 orders 取订单号与归属客户）。
 * 只读 DTO，不参与写库，故不带 {@code @TableName}；控制器据 customerId 做归属隔离。
 */
public class RefundDetailPO {

    private String refundNo;
    private String orderNo;
    private String status;
    private Long amountCent;
    private String type;
    private String reason;
    private String applicantType;
    private Long appealCount;
    private Long customerId;
    private LocalDateTime createdAt;
    private LocalDateTime refundedAt;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getApplicantType() {
        return applicantType;
    }

    public void setApplicantType(String applicantType) {
        this.applicantType = applicantType;
    }

    public Long getAppealCount() {
        return appealCount;
    }

    public void setAppealCount(Long appealCount) {
        this.appealCount = appealCount;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }

    public void setRefundedAt(LocalDateTime refundedAt) {
        this.refundedAt = refundedAt;
    }
}
