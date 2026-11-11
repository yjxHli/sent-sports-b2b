package com.sent.sports.auth.repository;

import com.sent.sports.auth.domain.UserAccount;

import java.util.Optional;

/**
 * 账号仓储端口（P3-61 引入，实现见 Phase 5）：按用户名/手机号/刷新令牌读取与持久化账号。
 *
 * <p>客户账号以「手机号」作为登录标识，登录链路优先走 {@link #findByPhone(String)}；
 * 员工账号仍按 {@link #findByUsername(String)} 查询。</p>
 */
public interface UserAccountRepository {
    Optional<UserAccount> findByUsername(String username);

    /** 按手机号查询客户账号（无此手机号时返回空）。 */
    Optional<UserAccount> findByPhone(String phone);

    Optional<UserAccount> findByRefreshToken(String refreshToken);

    void save(UserAccount account);
}
