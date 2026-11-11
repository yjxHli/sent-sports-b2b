package com.sent.sports.common.service.impl;

import com.sent.sports.common.service.ConfirmTokenStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内确认令牌存储（P5-31 默认实现）：{@code ConcurrentHashMap} + 惰性过期。
 * <p>单机够用；多实例部署时替换为 Redis 实现（key 不变，TTL 交给 Redis 的 EXPIRE）。
 * 惰性过期意味着过期的键只是「查不到」，不占额外清理线程——存储量极小，不做后台清扫。
 */
@Service
public class InMemoryConfirmTokenStore implements ConfirmTokenStore {

    private final Clock clock;
    private final Map<String, Stored> store = new ConcurrentHashMap<>();

    public InMemoryConfirmTokenStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Stored find(String key) {
        Stored stored = store.get(key);
        if (stored == null) {
            return null;
        }
        if (stored.expireAtMillis() <= clock.instant().toEpochMilli()) {
            store.remove(key);
            return null;
        }
        return stored;
    }

    @Override
    public void save(String key, String token, long expireAtMillis) {
        store.put(key, new Stored(token, expireAtMillis));
    }

    @Override
    public boolean consume(String key, String token) {
        Stored stored = find(key);
        if (stored == null || !stored.token().equals(token)) {
            return false;
        }
        store.remove(key);
        return true;
    }
}
