package com.sent.sports.common.idempotent;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等存储（进程内适配器）：PROCESSING → DONE 两态 + 惰性过期。
 * <p>本期按「先跑通」取进程内实现；多实例部署替换为 Redis（SETNX / SET result），
 * 拦截器逻辑完全不用改。数据库侧的幂等仍以订单 {@code uk_order_idem} 为最后一道闸。
 */
@Component
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final String PROCESSING = "PROCESSING";
    private static final String DONE = "DONE";

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryIdempotencyStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean tryStart(String key, Duration ttl) {
        long now = clock.instant().toEpochMilli();
        return store.putIfAbsent(key, new Entry(PROCESSING, null, now + ttl.toMillis())) == null;
    }

    @Override
    public boolean isProcessing(String key) {
        Entry entry = live(key);
        return entry != null && PROCESSING.equals(entry.state());
    }

    @Override
    public Optional<String> doneResult(String key) {
        Entry entry = live(key);
        return entry != null && DONE.equals(entry.state())
                ? Optional.ofNullable(entry.result())
                : Optional.empty();
    }

    @Override
    public void complete(String key, String responseJson, Duration ttl) {
        store.put(key, new Entry(DONE, responseJson, clock.instant().toEpochMilli() + ttl.toMillis()));
    }

    @Override
    public void release(String key) {
        store.remove(key);
    }

    private Entry live(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expireAtMillis() <= clock.instant().toEpochMilli()) {
            store.remove(key);
            return null;
        }
        return entry;
    }

    private record Entry(String state, String result, long expireAtMillis) {
    }
}
