package com.sent.sports.auth.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.Objects;

/**
 * 账号聚合（auth 模块，P3-61/P3-63）：纯 POJO，无 Spring 注解。
 * 负责密码校验、失败锁定（5 次锁定 15 分钟）、刷新令牌轮换与失效。
 * 注：passwordEncrypted 此处为明文占位，Phase 5 接入 BCrypt 后改为密文比对。
 */
public class UserAccount {

    public static final String ACTIVE = "ACTIVE";
    public static final String LOCKED = "LOCKED";
    private static final long LOCK_WINDOW_MILLIS = 15L * 60 * 1000;
    private static final int MAX_ATTEMPTS = 5;

    private final long userId;
    private final String username;
    /** 手机号（客户账号以手机号作为登录标识；员工账号此值为 null）。 */
    private final String phone;
    private final String passwordEncrypted;
    private String status;
    private int failedAttempts;
    private long lockUntilMillis;
    private long tokenVersion;
    private String currentRefreshToken;

    public UserAccount(long userId, String username, String passwordEncrypted) {
        this(userId, username, null, passwordEncrypted);
    }

    public UserAccount(long userId, String username, String phone, String passwordEncrypted) {
        this.userId = userId;
        this.username = username;
        this.phone = phone;
        this.passwordEncrypted = passwordEncrypted;
        this.status = ACTIVE;
        this.failedAttempts = 0;
        this.lockUntilMillis = 0L;
        this.tokenVersion = 0L;
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

    public String getUsername() {
        return username;
    }

    public String getPhone() {
        return phone;
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

    public String getRefreshToken() {
        return currentRefreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.currentRefreshToken = refreshToken;
    }
}
