package com.sent.sports.auth.repository.impl;

import com.sent.sports.auth.domain.UserAccount;
import com.sent.sports.auth.repository.UserAccountRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账号仓储（Phase 5 装配用进程内适配器）：进程内保存账号，登录/刷新/改密链路可跑通。
 *
 * <p>索引口径：{@code byUsername} 保存全部账号（管理员/客服/业务岗/客户）；
 * {@code byPhone} 仅保存带手机号的客户账号，供「手机号 + 密码」登录使用。
 * 接真库时替换本类为基于 {@code sys_staff / customer} 表的实现即可，用例层零改动。</p>
 */
@Repository
public class InMemoryUserAccountRepository implements UserAccountRepository {

    private final Map<String, UserAccount> byUsername = new ConcurrentHashMap<>();
    private final Map<String, UserAccount> byPhone = new ConcurrentHashMap<>();

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(byUsername.get(username));
    }

    @Override
    public Optional<UserAccount> findByPhone(String phone) {
        return Optional.ofNullable(byPhone.get(phone));
    }

    @Override
    public Optional<UserAccount> findByRefreshToken(String refreshToken) {
        if (refreshToken == null) {
            return Optional.empty();
        }
        return byUsername.values().stream()
                .filter(a -> refreshToken.equals(a.getRefreshToken()))
                .findFirst();
    }

    @Override
    public void save(UserAccount account) {
        byUsername.put(account.getUsername(), account);
        if (account.getPhone() != null && !account.getPhone().isBlank()) {
            byPhone.put(account.getPhone(), account);
        }
    }
}
