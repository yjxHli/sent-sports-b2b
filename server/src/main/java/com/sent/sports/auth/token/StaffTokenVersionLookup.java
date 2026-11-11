package com.sent.sports.auth.token;

import com.sent.sports.system.mapper.StaffMapper;
import org.springframework.stereotype.Component;

/**
 * 令牌版本查询：后台员工读 {@code sys_staff.token_version}。
 * <p>版本不匹配即代表改密/停用/强制下线后旧令牌应失效（{@code TOKEN_EXPIRED}）。
 * 用户端暂返回 0（客户令牌版本表在 V3 已有 token_version 列，接入客户 Mapper 后可补齐）。
 */
@Component
public class StaffTokenVersionLookup implements TokenVersionLookup {

    private final StaffMapper staffMapper;

    public StaffTokenVersionLookup(StaffMapper staffMapper) {
        this.staffMapper = staffMapper;
    }

    @Override
    public long currentVersion(String aud, long userId) {
        if (!"admin".equals(aud)) {
            return 0L;
        }
        Long version = staffMapper.selectTokenVersion(userId);
        return version == null ? 0L : version;
    }
}
