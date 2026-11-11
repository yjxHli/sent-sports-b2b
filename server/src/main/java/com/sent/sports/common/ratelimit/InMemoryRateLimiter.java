package com.sent.sports.common.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流器（进程内滑动窗口）：每个 key 一个时间戳队列，窗口外的时间戳出队后再计数。
 * <p>单机够用；多实例替换为 Redis 实现（ZSET 滑窗）即可，拦截器不改。
 */
@Component
public class InMemoryRateLimiter implements RateLimiter {

    private final Map<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String key, int limit, Duration window) {
        if (limit <= 0) {
            return false;
        }
        long now = clock.instant().toEpochMilli();
        long from = now - window.toMillis();
        Deque<Long> hits = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() < from) {
                hits.pollFirst();
            }
            if (hits.size() >= limit) {
                return false;
            }
            hits.addLast(now);
            return true;
        }
    }
}
