package com.sent.sports.auth.token;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 刷新令牌进程内存储（P5-25 默认实现）。
 * <p>两张索引：{@code byToken}（令牌 → 会话）与 {@code byDevice}（aud|userId|deviceId → 当前令牌）。
 * 轮换用 {@link ConcurrentHashMap#compute} 在键上原子完成「校验 + 删除旧 + 写新」，
 * 因此并发刷新同一令牌时只有一个能成功——<b>一次性</b>不依赖分布式锁。
 * 惰性清过期：读时判断并删除，不引入后台线程。
 */
@Component
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private final Map<String, Session> byToken = new ConcurrentHashMap<>();
    private final Map<String, String> byDevice = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRefreshTokenStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public void save(String aud, long userId, String deviceId, String token, long expireAtMillis) {
        String deviceKey = deviceKey(aud, userId, deviceId);
        String previous = byDevice.put(deviceKey, token);
        if (previous != null && !previous.equals(token)) {
            byToken.remove(previous);
        }
        byToken.put(token, new Session(aud, userId, deviceId, expireAtMillis));
    }

    @Override
    public Optional<Session> rotate(String oldToken, String newToken, long newExpireAtMillis) {
        if (oldToken == null || newToken == null) {
            return Optional.empty();
        }
        final Session[] rotated = new Session[1];
        byToken.compute(oldToken, (key, current) -> {
            if (current == null || current.expireAtMillis() <= clock.instant().toEpochMilli()) {
                return null; // 不存在/已过期：不写入新令牌
            }
            rotated[0] = current;
            return null;     // 旧令牌用后即焚
        });
        if (rotated[0] == null) {
            return Optional.empty();
        }
        Session session = rotated[0];
        byDevice.put(deviceKey(session.aud(), session.userId(), session.deviceId()), newToken);
        byToken.put(newToken, new Session(session.aud(), session.userId(), session.deviceId(), newExpireAtMillis));
        return Optional.of(session);
    }

    @Override
    public void revokeAll(String aud, long userId) {
        byToken.forEach((token, session) -> {
            if (session.aud().equals(aud) && session.userId() == userId) {
                byToken.remove(token);
                byDevice.remove(deviceKey(aud, userId, session.deviceId()), token);
            }
        });
    }

    @Override
    public void revoke(String token) {
        if (token == null) {
            return;
        }
        Session session = byToken.remove(token);
        if (session != null) {
            String deviceKey = deviceKey(session.aud(), session.userId(), session.deviceId());
            byDevice.remove(deviceKey, token);
        }
    }

    @Override
    public Optional<Session> find(String token) {
        Session session = byToken.get(token);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expireAtMillis() <= clock.instant().toEpochMilli()) {
            byToken.remove(token);
            return Optional.empty();
        }
        return Optional.of(session);
    }

    private String deviceKey(String aud, long userId, String deviceId) {
        return aud + "|" + userId + "|" + (deviceId == null ? "" : deviceId);
    }
}
