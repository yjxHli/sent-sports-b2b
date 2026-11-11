package com.sent.sports.customer.mapper.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

/**
 * 客户持久化对象（收口阶段）：映射 V3 的 {@code customer} 表。
 * <p>仅承载行形状；会员等级 / 积分 / 成长值直接落在该表
 * （{@code level_code} / {@code points_balance} / {@code growth_value}），无独立 member 表。
 * 密码哈希等敏感字段只在此 PO 落库，绝不被 {@code MeVO} 等对外 VO 透出。</p>
 */
@TableName("customer")
public class CustomerPO extends BasePO {

    private String customerNo;
    private String phone;
    private String passwordHash;
    private String nickname;
    private String email;
    private String type;
    private String levelCode;
    private Long growthValue;
    private Long pointsBalance;
    private Long ownerStaffId;
    private String status;
    private Long tokenVersion;
    private Integer deleted;

    /**
     * 以下三列不落在 {@code customer} 表，只由管理端列表的 LEFT JOIN
     * {@code enterprise_profile} 带出。不加 {@code exist = false} 会让 MyBatis-Plus
     * 把它们当成真实列拼进 INSERT / UPDATE。
     */
    @TableField(exist = false)
    private Long creditLimitCent;

    @TableField(exist = false)
    private Long creditUsedCent;

    @TableField(exist = false)
    private String enterpriseStatus;

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

    public String getEnterpriseStatus() {
        return enterpriseStatus;
    }

    public void setEnterpriseStatus(String enterpriseStatus) {
        this.enterpriseStatus = enterpriseStatus;
    }

    public String getCustomerNo() {
        return customerNo;
    }

    public void setCustomerNo(String customerNo) {
        this.customerNo = customerNo;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLevelCode() {
        return levelCode;
    }

    public void setLevelCode(String levelCode) {
        this.levelCode = levelCode;
    }

    public Long getGrowthValue() {
        return growthValue;
    }

    public void setGrowthValue(Long growthValue) {
        this.growthValue = growthValue;
    }

    public Long getPointsBalance() {
        return pointsBalance;
    }

    public void setPointsBalance(Long pointsBalance) {
        this.pointsBalance = pointsBalance;
    }

    public Long getOwnerStaffId() {
        return ownerStaffId;
    }

    public void setOwnerStaffId(Long ownerStaffId) {
        this.ownerStaffId = ownerStaffId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(Long tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
