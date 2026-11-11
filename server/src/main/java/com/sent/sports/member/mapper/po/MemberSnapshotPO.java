package com.sent.sports.member.mapper.po;

/**
 * 会员概览快照（读模型）：一位客户的等级 / 积分 / 企业授信一次查出。
 * <p>不是任何单表的完整映射，只承载管理端「会员与积分」首屏需要的列，
 * 由 {@code MemberOverviewMapper} 的 LEFT JOIN 填充；企业档案缺失时后三列为 null。</p>
 */
public class MemberSnapshotPO {

    private Long customerId;
    private String nickname;
    private String phone;
    private String type;
    private String levelCode;
    private Long growthValue;
    private Long pointsBalance;
    private Long creditLimitCent;
    private Long creditUsedCent;
    private String enterpriseStatus;

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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
}
