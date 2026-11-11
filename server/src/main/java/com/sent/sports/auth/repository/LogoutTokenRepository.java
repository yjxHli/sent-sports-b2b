package com.sent.sports.auth.repository;

import com.sent.sports.auth.domain.LogoutToken;

/**
 * 登出令牌黑名单仓储端口（P3-63 引入，实现见 Phase 5）：判断是否已失效。
 */
public interface LogoutTokenRepository {
    void save(LogoutToken token);

    boolean exists(String token);
}
