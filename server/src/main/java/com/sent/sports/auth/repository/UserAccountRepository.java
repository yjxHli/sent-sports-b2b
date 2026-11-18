package com.sent.sports.auth.repository;

import com.sent.sports.auth.domain.UserAccount;

import java.util.Optional;

/**
 * 账号仓储端口（2026-11-18 起唯一实现为 MyBatis-Plus 数据库仓储）。
 *
 * <p>客户账号以「手机号」作为登录标识，员工账号按 {@code sys_staff.username} 登录；
 * 工号 {@code staff_no} 是完全独立的业务字段，绝不作为登录名使用。</p>
 */
public interface UserAccountRepository {
    /** 员工登录名（sys_staff.username）。 */
    Optional<UserAccount> findByUsername(String username);

    /** 客户手机号（customer.phone）。 */
    Optional<UserAccount> findByPhone(String phone);

    /** 按登录端 + 主键读取（刷新令牌轮换时按 store 里的会话信息回读账号）。 */
    Optional<UserAccount> findById(String aud, long userId);

    /** 保存账号状态（密码 / 锁定计数 / 状态），不写 token_version。 */
    void save(UserAccount account);
}
