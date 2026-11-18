package com.sent.sports.coupon.mapper.po;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 券模板行。刻意不继承 {@code BasePO}：coupon_template 表没有 created_by 列，
 * 继承后 MyBatis-Plus 会拼出该列导致 Unknown column。
 */
@TableName("coupon_template")
public class CouponTemplatePO {

    private long id;
    /** 券号 CP TyyyyMMdd+6 位序列；管理端列表展示、运营口头对账都用它 */
    private String templateNo;
    private String name;
    private long amountCent;
    private long thresholdCent;
    /** NO_THRESHOLD / FULL_REDUCTION / CATEGORY / ENTERPRISE */
    private String couponType;
    /** AMOUNT 金额券 / PERCENT 折扣券 */
    private String discountType;
    /** 折扣券的支付比例：90 = 9 折（减 10%）；金额券固定 100 */
    private int discountRate;
    /** ALL / SPU / SKU */
    private String scopeType;
    /** 逗号分隔的 SPU 或 SKU 主键；ALL 时为空 */
    private String scopeIds;
    /** ALL / ENTERPRISE */
    private String customerScope;
    private int claimable;
    private int claimLimit;
    /** -1 = 不限量 */
    private int totalQty;
    private int claimedQty;
    private int validDays;
    private String scene;
    private String status;
    /** 创建人员工 ID（sys_staff.id）；存量模板回填 9001 */
    private Long createdBy;
    /** 0 正常 / 1 已删除（软删，已发出的券不受影响） */
    private int deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 非表字段：列表 LEFT JOIN sys_staff 带出的创建人姓名 */
    private String creatorName;

    public String getCreatorName() {
        return creatorName;
    }

    public void setCreatorName(String creatorName) {
        this.creatorName = creatorName;
    }

    public String getTemplateNo() {
        return templateNo;
    }

    public void setTemplateNo(String templateNo) {
        this.templateNo = templateNo;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public int getDeleted() {
        return deleted;
    }

    public void setDeleted(int deleted) {
        this.deleted = deleted;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
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

    public int getClaimable() {
        return claimable;
    }

    public void setClaimable(int claimable) {
        this.claimable = claimable;
    }

    public int getClaimLimit() {
        return claimLimit;
    }

    public void setClaimLimit(int claimLimit) {
        this.claimLimit = claimLimit;
    }

    public int getTotalQty() {
        return totalQty;
    }

    public void setTotalQty(int totalQty) {
        this.totalQty = totalQty;
    }

    public int getClaimedQty() {
        return claimedQty;
    }

    public void setClaimedQty(int claimedQty) {
        this.claimedQty = claimedQty;
    }

    public int getValidDays() {
        return validDays;
    }

    public void setValidDays(int validDays) {
        this.validDays = validDays;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
