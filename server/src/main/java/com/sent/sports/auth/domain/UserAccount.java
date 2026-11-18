package com.sent.sports.auth.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.Objects;

/**
 * 账号聚合（auth 模块，P3-61/P3-63）：纯 POJO，无 Spring 注解。
 * 负责密码校验、失败锁定（5 次锁定 15 分钟）、刷新令牌轮换与失效。
 * <p>2026-11-18 认证重构：账号数据全部来自数据库（sys_staff / customer），本对象只是行快照。
 * {@code aud} 决定仓储把状态写回哪张表；{@code staffNo} 是工号，与登录名 {@code username} 完全独立。</p>
 */
public class UserAccount {

    public static final String ACTIVE = "ACTIVE";
    public static final String LOCKED = "LOCKED";
    public static final String AUD_ADMIN = "admin";
    public static final String AUD_USER = "user";
    private static final long LOCK_WINDOW_MILLIS = 15L * 60 * 1000;
    private static final int MAX_ATTEMPTS = 5;

    private final long userId;
    private final String aud;
    private final String username;
    private final String staffNo;
    /** 手机号（客户账号以手机号作为登录标识；员工账号此值为 null）。 */
    private final String phone;
    private final String passwordEncrypted;
    private String status;
    private int failedAttempts;
    private long lockUntilMillis;
    private long tokenVersion;
    /** 客户类型：PERSON / ENTERPRISE；员工账号为 null。 */
    private final String customerType;
    /** 企业审核状态：PENDING / ACTIVE / REJECTED；个人客户与员工为 null。 */
    private final String enterpriseStatus;
    private String currentRefreshToken;

    /** 员工账号：用户名即登录名，aud 固定 admin。 */
    public UserAccount(long userId, String username, String passwordEncrypted) {
        this(userId, AUD_ADMIN, username, null, null, passwordEncrypted);
    }

    /** 客户账号：username 与 phone 同为手机号，aud 固定 user。 */
    public UserAccount(long userId, String username, String phone, String passwordEncrypted) {
        this(userId, AUD_USER, username, null, phone, passwordEncrypted);
    }

    /** 完整构造器（数据库仓储还原行时使用）。 */
    public UserAccount(long userId, String aud, String username, String staffNo, String phone,
                       String passwordEncrypted) {
        this(userId, aud, username, staffNo, phone, passwordEncrypted,
                ACTIVE, 0, 0L, 0L, null, null);
    }

    /** 从数据库行还原账号状态，不触发任何状态机。 */
    public static UserAccount rehydrate(long userId, String aud, String username, String staffNo, String phone,
                                        String passwordEncrypted, String status, int failedAttempts,
                                        long lockUntilMillis, long tokenVersion) {
        return rehydrate(userId, aud, username, staffNo, phone, passwordEncrypted, status,
                failedAttempts, lockUntilMillis, tokenVersion, null, null);
    }

    /** 客户账号专用还原：带客户类型与企业审核状态，供登录门禁判断。 */
    public static UserAccount rehydrate(long userId, String aud, String username, String staffNo, String phone,
                                        String passwordEncrypted, String status, int failedAttempts,
                                        long lockUntilMillis, long tokenVersion,
                                        String customerType, String enterpriseStatus) {
        return new UserAccount(userId, aud, username, staffNo, phone, passwordEncrypted,
                status, failedAttempts, lockUntilMillis, tokenVersion, customerType, enterpriseStatus);
    }

    private UserAccount(long userId, String aud, String username, String staffNo, String phone,
                        String passwordEncrypted, String status, int failedAttempts,
                        long lockUntilMillis, long tokenVersion,
                        String customerType, String enterpriseStatus) {
        this.userId = userId;
        this.aud = aud;
        this.username = username;
        this.staffNo = staffNo;
        this.phone = phone;
        this.passwordEncrypted = passwordEncrypted;
        this.status = status;
        this.failedAttempts = failedAttempts;
        this.lockUntilMillis = lockUntilMillis;
        this.tokenVersion = tokenVersion;
        this.customerType = customerType;
        this.enterpriseStatus = enterpriseStatus;
        this.currentRefreshToken = null;
    }

    /** 校验密码：锁定或错误均抛 BIZ_REJECT；成功则清空失败计数。失败计数由调用方落库。 */
    public void authenticate(String rawPassword, long nowMillis) {
        if (isLocked(nowMillis)) {
            throw new BizException(ErrorCode.BIZ_REJECT);
        }
        if (!Objects.equals(passwordEncrypted, rawPassword)) {
            recordFailure(nowMillis);
            throw new BizException(ErrorCode.BIZ_REJECT);
        }
        resetFailures();
    }

    private void recordFailure(long nowMillis) {
        failedAttempts++;
        if (failedAttempts >= MAX_ATTEMPTS) {
            status = LOCKED;
            lockUntilMillis = nowMillis + LOCK_WINDOW_MILLIS;
        }
    }

    private void resetFailures() {
        failedAttempts = 0;
        lockUntilMillis = 0L;
        status = ACTIVE;
    }

    public boolean isLocked(long nowMillis) {
        return LOCKED.equals(status) && nowMillis < lockUntilMillis;
    }

    /** 刷新令牌轮换：版本号 +1，写入新 RT（旧 RT 由调用方加入黑名单）。 */
    public void rotateToken(String refreshToken) {
        this.tokenVersion++;
        this.currentRefreshToken = refreshToken;
    }

    public long getUserId() {
        return userId;
    }

    public String getAud() {
        return aud;
    }

    public String getUsername() {
        return username;
    }

    /** 工号（仅员工账号），与登录名分离。 */
    public String getStaffNo() {
        return staffNo;
    }

    public String getPhone() {
        return phone;
    }

    /** 落库用：当前密码（项目沿用明文占位口径）。 */
    public String getPasswordHash() {
        return passwordEncrypted;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public String getStatus() {
        return status;
    }

    public long getLockUntilMillis() {
        return lockUntilMillis;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public String getCustomerType() {
        return customerType;
    }

    public String getEnterpriseStatus() {
        return enterpriseStatus;
    }

    public String getRefreshToken() {
        return currentRefreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.currentRefreshToken = refreshToken;
    }
}
