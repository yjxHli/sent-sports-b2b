package com.sent.sports.auth.token;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 刷新令牌进程内存储单测（P5-25）：一次性轮换、按设备隔离、过期失效、全端撤销与并发抢刷新。
 * 时钟用可变的 {@link MutableClock}，避免 Mockito 严格桩约束，也能精确跨过过期点。
 */
class InMemoryRefreshTokenStoreTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long TTL = 7L * 24 * 3600 * 1000;

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore(clock);

    @Test
    void saveThenFindReturnsSession() {
        store.save("admin", 7L, "web", "RT1", NOW + TTL);

        Optional<RefreshTokenStore.Session> found = store.find("RT1");

        assertTrue(found.isPresent());
        assertEquals("admin", found.get().aud());
        assertEquals(7L, found.get().userId());
        assertEquals("web", found.get().deviceId());
        assertEquals(NOW + TTL, found.get().expireAtMillis());
    }

    @Test
    void rotateIsOneTimeAndOldTokenDies() {
        store.save("admin", 7L, "web", "RT1", NOW + TTL);

        Optional<RefreshTokenStore.Session> rotated = store.rotate("RT1", "RT2", NOW + 2 * TTL);

        assertTrue(rotated.isPresent());
        assertEquals(7L, rotated.get().userId());
        assertFalse(store.find("RT1").isPresent(), "旧 RT 必须立即失效");
        assertTrue(store.find("RT2").isPresent());
        assertEquals(NOW + 2 * TTL, store.find("RT2").get().expireAtMillis());
        assertTrue(store.rotate("RT1", "RT3", NOW + 3 * TTL).isEmpty(), "旧 RT 重放应失败");
        assertFalse(store.find("RT3").isPresent(), "重放失败时不得写入新令牌");
    }

    @Test
    void rotateRejectsExpiredToken() {
        store.save("admin", 7L, "web", "RT1", NOW + 1000L);
        clock.setMillis(NOW + 1000L);

        assertTrue(store.rotate("RT1", "RT2", NOW + TTL).isEmpty());
        assertFalse(store.find("RT1").isPresent(), "过期读取应惰性清理");
        assertFalse(store.find("RT2").isPresent());
    }

    @Test
    void rotateRejectsUnknownToken() {
        assertTrue(store.rotate("MISSING", "RT2", NOW + TTL).isEmpty());
        assertFalse(store.find("RT2").isPresent());
    }

    @Test
    void sameDeviceLoginReplacesOnlyThatDevice() {
        store.save("admin", 7L, "web", "RT-WEB-1", NOW + TTL);
        store.save("admin", 7L, "app", "RT-APP-1", NOW + TTL);

        store.save("admin", 7L, "web", "RT-WEB-2", NOW + TTL);

        assertFalse(store.find("RT-WEB-1").isPresent(), "同设备旧令牌应被覆盖");
        assertTrue(store.find("RT-WEB-2").isPresent());
        assertTrue(store.find("RT-APP-1").isPresent(), "其他设备不受影响");
    }

    @Test
    void revokeAllOnlyAffectsGivenAudAndUser() {
        store.save("admin", 7L, "web", "A1", NOW + TTL);
        store.save("admin", 7L, "app", "A2", NOW + TTL);
        store.save("admin", 8L, "web", "B1", NOW + TTL);
        store.save("user", 7L, "web", "U1", NOW + TTL);

        store.revokeAll("admin", 7L);

        assertFalse(store.find("A1").isPresent());
        assertFalse(store.find("A2").isPresent());
        assertTrue(store.find("B1").isPresent());
        assertTrue(store.find("U1").isPresent(), "不同登录端不算同一用户");
    }

    @Test
    void revokeRemovesOnlyOneToken() {
        store.save("admin", 7L, "web", "A1", NOW + TTL);
        store.save("admin", 7L, "app", "A2", NOW + TTL);

        store.revoke("A1");

        assertFalse(store.find("A1").isPresent());
        assertTrue(store.find("A2").isPresent());
    }

    @Test
    void concurrentRotateOnlyOneWins() throws Exception {
        store.save("admin", 7L, "web", "RT1", NOW + TTL);
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                String newToken = "RT-" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return store.rotate("RT1", newToken, NOW + TTL).isPresent();
                }));
            }
            start.countDown();
            int wins = 0;
            for (Future<Boolean> f : futures) {
                if (f.get(5, TimeUnit.SECONDS)) {
                    wins++;
                }
            }
            assertEquals(1, wins, "同一旧 RT 并发刷新只能成功一次");
        } finally {
            pool.shutdownNow();
        }
    }

    /** 可推进的测试时钟：固定 UTC，避免环境时区影响。 */
    private static final class MutableClock extends Clock {

        private volatile long millis;

        private MutableClock(long millis) {
            this.millis = millis;
        }

        private void setMillis(long millis) {
            this.millis = millis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }
}
