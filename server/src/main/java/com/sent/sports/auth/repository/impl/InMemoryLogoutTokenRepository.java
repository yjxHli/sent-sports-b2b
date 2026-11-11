package com.sent.sports.auth.repository.impl;

import com.sent.sports.auth.domain.LogoutToken;
import com.sent.sports.auth.repository.LogoutTokenRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 登出令牌黑名单（进程内适配器；多实例部署替换为 Redis SETEX）。 */
@Repository
public class InMemoryLogoutTokenRepository implements LogoutTokenRepository {

    private final Map<String, LogoutToken> revoked = new ConcurrentHashMap<>();

    @Override
    public void save(LogoutToken token) {
        revoked.put(token.token(), token);
    }

    @Override
    public boolean exists(String token) {
        return token != null && revoked.containsKey(token);
    }
}
