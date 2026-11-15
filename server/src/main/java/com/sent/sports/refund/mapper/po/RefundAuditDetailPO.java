package com.sent.sports.refund.mapper.po;

import java.time.LocalDateTime;

/**
 * 管理端退款审核详情投影（只读）。
 *
 * <p>一次联查把审核页要的三块拼在一起：退款单（{@code refund_order}）、
 * 订单头（{@code orders}）、客户（{@code customer}），外加最近一条物流（{@code shipment}）。
 * 审核员打开详情页只发一次请求，不必再拉订单详情接口。</p>
 */
public class RefundAuditDetailPO {

    private String refundNo;
    private String orderNo;
    private String status;
    private Long amountCent;
    private Long pointRefund;
    private String type;
    private String reason;
    private String applicantType;
    private Long appealCount;
    /** 用户上传的凭证图片（JSON 数组原文），查询层解码后出参。 */
    private String imagesJson;
    private String auditRemark;
    private Long orderItemId;
    private LocalDateTime createdAt;
    private LocalDateTime refundedAt;

    private Long orderId;
    private String orderStatus;
    private String payStatus;
    private String afterSaleStatus;
    private Long goodsAmountCent;
    private Long freightCent;
    private Long pointDeductCent;
    private Long discountCent;
    private Long payableCent;
    private Long paidCent;
    private LocalDateTime orderCreatedAt;
    private LocalDateTime paidAt;
    private LocalDateTime completedAt;
    private String orderRemark;
    private String addressJson;

    private Long customerId;
    private String customerName;
    private String customerPhone;
    private String customerNo;
    private String customerType;
    private String customerLevel;

    private String carrier;
    private String trackingNo;
    private LocalDateTime shippedAt;

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

    public Long getPointRefund() {
        return pointRefund;
    }

    public void setPointRefund(Long pointRefund) {
        this.pointRefund = pointRefund;
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

    public String getImagesJson() {
        return imagesJson;
    }

    public void setImagesJson(String imagesJson) {
        this.imagesJson = imagesJson;
    }

    public String getAuditRemark() {
        return auditRemark;
    }

    public void setAuditRemark(String auditRemark) {
        this.auditRemark = auditRemark;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
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

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public String getAfterSaleStatus() {
        return afterSaleStatus;
    }

    public void setAfterSaleStatus(String afterSaleStatus) {
        this.afterSaleStatus = afterSaleStatus;
    }

    public Long getGoodsAmountCent() {
        return goodsAmountCent;
    }

    public void setGoodsAmountCent(Long goodsAmountCent) {
        this.goodsAmountCent = goodsAmountCent;
    }

    public Long getFreightCent() {
        return freightCent;
    }

    public void setFreightCent(Long freightCent) {
        this.freightCent = freightCent;
    }

    public Long getPointDeductCent() {
        return pointDeductCent;
    }

    public void setPointDeductCent(Long pointDeductCent) {
        this.pointDeductCent = pointDeductCent;
    }

    public Long getDiscountCent() {
        return discountCent;
    }

    public void setDiscountCent(Long discountCent) {
        this.discountCent = discountCent;
    }

    public Long getPayableCent() {
        return payableCent;
    }

    public void setPayableCent(Long payableCent) {
        this.payableCent = payableCent;
    }

    public Long getPaidCent() {
        return paidCent;
    }

    public void setPaidCent(Long paidCent) {
        this.paidCent = paidCent;
    }

    public LocalDateTime getOrderCreatedAt() {
        return orderCreatedAt;
    }

    public void setOrderCreatedAt(LocalDateTime orderCreatedAt) {
        this.orderCreatedAt = orderCreatedAt;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getOrderRemark() {
        return orderRemark;
    }

    public void setOrderRemark(String orderRemark) {
        this.orderRemark = orderRemark;
    }

    public String getAddressJson() {
        return addressJson;
    }

    public void setAddressJson(String addressJson) {
        this.addressJson = addressJson;
    }

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

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerNo() {
        return customerNo;
    }

    public void setCustomerNo(String customerNo) {
        this.customerNo = customerNo;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public String getCustomerLevel() {
        return customerLevel;
    }

    public void setCustomerLevel(String customerLevel) {
        this.customerLevel = customerLevel;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public LocalDateTime getShippedAt() {
        return shippedAt;
    }

    public void setShippedAt(LocalDateTime shippedAt) {
        this.shippedAt = shippedAt;
    }
}
