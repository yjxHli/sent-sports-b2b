package com.sent.sports.auth.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sent.sports.auth.domain.UserAccount;
import com.sent.sports.auth.mapper.CustomerAccountMapper;
import com.sent.sports.auth.mapper.StaffAccountMapper;
import com.sent.sports.auth.mapper.po.CustomerAccountPO;
import com.sent.sports.auth.mapper.po.StaffAccountPO;
import com.sent.sports.auth.repository.UserAccountRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 账号仓储（2026-11-18 起唯一实现）：直连 MySQL，员工读 sys_staff、客户读 customer。
 * <p>登录名只读 {@code sys_staff.username}；工号 {@code staff_no} 原样保留、绝不用于登录。
 * 所有状态（失败计数 / 锁定时间 / 密码）都落库，重启不丢账号。</p>
 * <p><b>token_version 不写库</b>：JWT 的 ver 固定 0，而刷新令牌轮换会在内存里 +1；
 * 若把它写回库，AuthFilter 立即判旧令牌过期，刷新链路会自废。该列仍由管理端强制下线时维护。</p>
 */
@Repository
public class DbUserAccountRepositoryImpl implements UserAccountRepository {

    private final StaffAccountMapper staffAccountMapper;
    private final CustomerAccountMapper customerAccountMapper;

    public DbUserAccountRepositoryImpl(StaffAccountMapper staffAccountMapper,
                                   CustomerAccountMapper customerAccountMapper) {
        this.staffAccountMapper = staffAccountMapper;
        this.customerAccountMapper = customerAccountMapper;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        StaffAccountPO po = staffAccountMapper.selectOne(new LambdaQueryWrapper<StaffAccountPO>()
                .eq(StaffAccountPO::getUsername, username).last("LIMIT 1"));
        return Optional.ofNullable(po).map(this::toAccount);
    }

    @Override
    public Optional<UserAccount> findByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        CustomerAccountPO po = customerAccountMapper.selectByPhoneWithEnterprise(phone);
        return Optional.ofNullable(po).map(this::toAccount);
    }

    @Override
    public Optional<UserAccount> findById(String aud, long userId) {
        if (UserAccount.AUD_ADMIN.equals(aud)) {
            return Optional.ofNullable(staffAccountMapper.selectById(userId)).map(this::toAccount);
        }
        if (UserAccount.AUD_USER.equals(aud)) {
            return Optional.ofNullable(customerAccountMapper.selectByIdWithEnterprise(userId))
                    .map(this::toAccount);
        }
        return Optional.empty();
    }

    @Override
    public void save(UserAccount account) {
        if (UserAccount.AUD_ADMIN.equals(account.getAud())) {
            StaffAccountPO po = new StaffAccountPO();
            po.setId(account.getUserId());
            po.setUsername(account.getUsername());
            po.setStaffNo(account.getStaffNo());
            po.setPasswordHash(account.getPasswordHash());
            po.setStatus(account.getStatus());
            po.setFailCount(account.getFailedAttempts());
            po.setLockedUntil(toLocalDateTime(account.getLockUntilMillis()));
            staffAccountMapper.updateById(po);
            return;
        }
        CustomerAccountPO po = new CustomerAccountPO();
        po.setId(account.getUserId());
        po.setPasswordHash(account.getPasswordHash());
        po.setStatus(account.getStatus());
        po.setFailCount(account.getFailedAttempts());
        po.setLockedUntil(toLocalDateTime(account.getLockUntilMillis()));
        customerAccountMapper.updateById(po);
    }

    private UserAccount toAccount(StaffAccountPO po) {
        return UserAccount.rehydrate(po.getId(), UserAccount.AUD_ADMIN, po.getUsername(), po.getStaffNo(),
                po.getPhone(), po.getPasswordHash(), po.getStatus(),
                po.getFailCount() == null ? 0 : po.getFailCount(), toMillis(po.getLockedUntil()),
                po.getTokenVersion() == null ? 0L : po.getTokenVersion());
    }

    private UserAccount toAccount(CustomerAccountPO po) {
        return UserAccount.rehydrate(po.getId(), UserAccount.AUD_USER, po.getPhone(), null,
                po.getPhone(), po.getPasswordHash(), po.getStatus(),
                po.getFailCount() == null ? 0 : po.getFailCount(), toMillis(po.getLockedUntil()),
                po.getTokenVersion() == null ? 0L : po.getTokenVersion(),
                po.getType(), po.getEnterpriseStatus());
    }

    private static long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDateTime toLocalDateTime(long millis) {
        return millis <= 0 ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
