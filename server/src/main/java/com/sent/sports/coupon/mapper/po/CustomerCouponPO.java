package com.sent.sports.coupon.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 用户持有券行。不继承 BasePO：customer_coupon 表没有 created_by 列。 */
@TableName("customer_coupon")
public class CustomerCouponPO {

    private long id;
    private long customerId;
    private long templateId;
    private String couponNo;
    private String name;
    private long amountCent;
    private long thresholdCent;
    /** 发券时从模板快照下来的四类券型与适用范围 */
    private String couponType;
    private String discountType;
    private int discountRate;
    private String scopeType;
    private String scopeIds;
    private String customerScope;
    /** 领券中心领取凭证 CLAIM-{templateId}；管理端发放为 NULL，便于唯一索引防重复领取 */
    private String claimKey;
    private String status;
    /** 0 正常 / 1 已删除（发放记录软删；已使用券的订单折扣额不变） */
    private int deleted;
    private String source;
    private String reason;
    private LocalDateTime expireAt;
    private LocalDateTime usedAt;
    private String orderNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 非表字段：发放记录列表 LEFT JOIN customer 带出的客户昵称 */
    private String customerName;

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(long customerId) {
        this.customerId = customerId;
    }

    public long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(long templateId) {
        this.templateId = templateId;
    }

    public String getCouponNo() {
        return couponNo;
    }

    public void setCouponNo(String couponNo) {
        this.couponNo = couponNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getAmountCent() {
        return amountCent;
    }

    public void setAmountCent(long amountCent) {
        this.amountCent = amountCent;
    }

    public long getThresholdCent() {
        return thresholdCent;
    }

    public void setThresholdCent(long thresholdCent) {
        this.thresholdCent = thresholdCent;
    }

    public String getCouponType() {
        return couponType;
    }

    public void setCouponType(String couponType) {
        this.couponType = couponType;
    }

    public String getDiscountType() {
        return discountType;
    }

    public void setDiscountType(String discountType) {
        this.discountType = discountType;
    }

    public int getDiscountRate() {
        return discountRate;
    }

    public void setDiscountRate(int discountRate) {
        this.discountRate = discountRate;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public String getScopeIds() {
        return scopeIds;
    }

    public void setScopeIds(String scopeIds) {
        this.scopeIds = scopeIds;
    }

    public String getCustomerScope() {
        return customerScope;
    }

    public void setCustomerScope(String customerScope) {
        this.customerScope = customerScope;
    }

    public String getClaimKey() {
        return claimKey;
    }

    public void setClaimKey(String claimKey) {
        this.claimKey = claimKey;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getDeleted() {
        return deleted;
    }

    public void setDeleted(int deleted) {
        this.deleted = deleted;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
