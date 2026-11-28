package com.sent.sports.finance.mapper.po;

import java.time.LocalDateTime;

/**
 * 用户端企业档案投影（{@code /api/me/enterprise}）。
 * <p>只取本人可见字段：公司名、信用代码、认证状态、授信额度与账期、审核意见。
 * 不返回 {@code deleted} 等运营字段；软删档案直接查不到。</p>
 */
public class MyEnterpriseRow {

    private Long customerId;
    private String companyName;
    private String creditCode;
    private String licenseFileId;
    private String status;
    private Long creditLimitCent;
    private Long creditUsedCent;
    private Integer paymentTermDays;
    private LocalDateTime creditExpireAt;
    private String auditRemark;
    private LocalDateTime auditedAt;
    private LocalDateTime submittedAt;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getCreditCode() {
        return creditCode;
    }

    public void setCreditCode(String creditCode) {
        this.creditCode = creditCode;
    }

    public String getLicenseFileId() {
        return licenseFileId;
    }

    public void setLicenseFileId(String licenseFileId) {
        this.licenseFileId = licenseFileId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreditLimitCent() {
        return creditLimitCent;
    }

    public void setCreditLimitCent(Long creditLimitCent) {
        this.creditLimitCent = creditLimitCent;
    }

    public Long getCreditUsedCent() {
        return creditUsedCent;
    }

    public void setCreditUsedCent(Long creditUsedCent) {
        this.creditUsedCent = creditUsedCent;
    }

    public Integer getPaymentTermDays() {
        return paymentTermDays;
    }

    public void setPaymentTermDays(Integer paymentTermDays) {
        this.paymentTermDays = paymentTermDays;
    }

    public LocalDateTime getCreditExpireAt() {
        return creditExpireAt;
    }

    public void setCreditExpireAt(LocalDateTime creditExpireAt) {
        this.creditExpireAt = creditExpireAt;
    }

    public String getAuditRemark() {
        return auditRemark;
    }

    public void setAuditRemark(String auditRemark) {
        this.auditRemark = auditRemark;
    }

    public LocalDateTime getAuditedAt() {
        return auditedAt;
    }

    public void setAuditedAt(LocalDateTime auditedAt) {
        this.auditedAt = auditedAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
}
