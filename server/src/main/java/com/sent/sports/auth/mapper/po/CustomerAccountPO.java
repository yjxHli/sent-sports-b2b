package com.sent.sports.auth.mapper.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sent.sports.common.mybatis.BasePO;

import java.time.LocalDateTime;

/** 认证只读视图：customer 的登录相关列（手机号即登录名，V38 增锁定列）。 */
@TableName("customer")
public class CustomerAccountPO extends BasePO {

    private String customerNo;
    private String phone;
    private String passwordHash;
    private String nickname;
    private String type;
    private String status;
    /** 联查 enterprise_profile.status；不是 customer 表列，不能被 MyBatis-Plus 写回。 */
    @TableField(exist = false)
    private String enterpriseStatus;
    private Integer failCount;
    private LocalDateTime lockedUntil;
    private Long tokenVersion;
    private Integer deleted;

    public String getCustomerNo() { return customerNo; }
    public void setCustomerNo(String customerNo) { this.customerNo = customerNo; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getEnterpriseStatus() { return enterpriseStatus; }
    public void setEnterpriseStatus(String enterpriseStatus) { this.enterpriseStatus = enterpriseStatus; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getFailCount() { return failCount; }
    public void setFailCount(Integer failCount) { this.failCount = failCount; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }
    public Long getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(Long tokenVersion) { this.tokenVersion = tokenVersion; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
}
