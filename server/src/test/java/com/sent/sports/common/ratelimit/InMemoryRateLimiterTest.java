package com.sent.sports.common.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 进程内限流器单元测试（加固审查·第二步）：证明限流器无外部依赖、同步返回、永不阻塞。
 * <p>系统当前限流实现为 {@link InMemoryRateLimiter}（无 Redis 依赖）。因此「Redis 挂了 / 连接超时」
 * 这一故障模式在本系统中不存在——限流器始终可用且 fail-fast。下面用例验证：
 * 1) limit<=0 立即拒绝（配置安全网，避免被错误配置放大成放行或卡死）；
 * 2) 窗口内超限立即拒绝（有界，不阻塞、不抛异常）；
 * 3) 滑动窗口过期后旧命中被剔除，可再次放行；
 * 4) 批量高频调用同步返回，不会因锁或外部 IO 卡死（线程池耗尽防护）。</p>
 */
class InMemoryRateLimiterTest {

    private static final ZoneId ZONE = ZoneOffset.UTC;

    /** 可控时钟，便于测试滑动窗口过期。 */
    static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant start, ZoneId zone) {
            this.instant = start;
            this.zone = zone;
        }

        void advance(Duration d) {
            instant = instant.plus(d);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId z) {
            return new MutableClock(instant, z);
        }
    }

    @Test
    void limitZeroRejectsImmediatelyWithoutThrowing() {
        // 错误配置（limit<=0）应快速失败而非放行或卡死
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(Clock.systemUTC());
        boolean allowed = limiter.tryAcquire("login:1.2.3.4", 0, Duration.ofSeconds(30));
        assertThat(allowed).isFalse();
    }

    @Test
    void rejectsWhenWindowExhaustedAndNeverBlocks() {
        MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"), ZONE);
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        // 窗口 30s、上限 2 次
        assertThat(limiter.tryAcquire("k", 2, Duration.ofSeconds(30))).isTrue();
        assertThat(limiter.tryAcquire("k", 2, Duration.ofSeconds(30))).isTrue();
        // 第 3 次立即拒绝（有界、同步、无阻塞）
        assertThat(limiter.tryAcquire("k", 2, Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void slidingWindowExpiresOldHits() {
        MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"), ZONE);
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

        assertThat(limiter.tryAcquire("k", 1, Duration.ofSeconds(10))).isTrue();
        // 同窗口再次请求被拒
        assertThat(limiter.tryAcquire("k", 1, Duration.ofSeconds(10))).isFalse();

        // 时间推进超过窗口，旧命中被剔除，可再次放行
        clock.advance(Duration.ofSeconds(11));
        assertThat(limiter.tryAcquire("k", 1, Duration.ofSeconds(10))).isTrue();
    }

    @Test
    void highFrequencyCallsReturnSynchronouslyNoHang() {
        // 模拟 Redis 不可用场景下，本应「卡死」的退化情形：此处用进程内实现证明永不阻塞。
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(Clock.systemUTC());
        long start = System.nanoTime();
        int calls = 200_000;
        int allowed = 0;
        for (int i = 0; i < calls; i++) {
            if (limiter.tryAcquire("burst", 1, Duration.ofSeconds(1))) {
                allowed++;
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        // 20 万次调用应在极短时间内同步返回（无锁竞争卡死 / 无外部 IO）
        assertThat(elapsedMs).isLessThan(5_000L);
        // 窗口 1s、上限 1，仅首条放行
        assertThat(allowed).isEqualTo(1);
    }
}
