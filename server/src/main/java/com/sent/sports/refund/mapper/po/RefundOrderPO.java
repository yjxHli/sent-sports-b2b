package com.sent.sports.refund.mapper.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

import java.time.LocalDateTime;

/**
 * 退款单 {@code refund_order} 持久化对象（P5-15）。
 * <p>{@code status} 取 PENDING / REFUNDING / REFUNDED / FAILED / CLOSED，与 {@code RefundStatus} 一一对应。
 */
@TableName("refund_order")
public class RefundOrderPO extends BasePO {

    private String refundNo;
    private Long orderId;
    /** 来自 orders 联查的订单号（refund_order 本身不冗余存储），仅用于读侧投影，不参与写库。 */
    @TableField(exist = false)
    private String orderNo;
    /** 申诉次数（V20 新增列，默认 0）；初始化为 0L 以保证写库 INSERT 不为 NULL。 */
    private Long appealCount = 0L;
    private Long orderItemId;
    private String type;
    private Long amountCent;
    private Long pointRefund;
    private String status;
    private String reasonCode;
    private String remark;
    private String imagesJson;
    private String applicantType;
    private Long auditorId;
    private String auditRemark;
    private LocalDateTime refundedAt;

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getAppealCount() {
        return appealCount;
    }

    public void setAppealCount(Long appealCount) {
        this.appealCount = appealCount;
    }

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getImagesJson() {
        return imagesJson;
    }

    public void setImagesJson(String imagesJson) {
        this.imagesJson = imagesJson;
    }

    public String getApplicantType() {
        return applicantType;
    }

    public void setApplicantType(String applicantType) {
        this.applicantType = applicantType;
    }

    public Long getAuditorId() {
        return auditorId;
    }

    public void setAuditorId(Long auditorId) {
        this.auditorId = auditorId;
    }

    public String getAuditRemark() {
        return auditRemark;
    }

    public void setAuditRemark(String auditRemark) {
        this.auditRemark = auditRemark;
    }

    public LocalDateTime getRefundedAt() {
        return refundedAt;
    }

    public void setRefundedAt(LocalDateTime refundedAt) {
        this.refundedAt = refundedAt;
    }
}
