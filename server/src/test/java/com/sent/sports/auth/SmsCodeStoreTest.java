package com.sent.sports.auth;

import com.sent.sports.auth.repository.impl.InMemorySmsCodeStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证码内存存储测试（TDD 红）：一次性消费、错误码不消耗、过期作废。
 * 存储不依赖 Clock，时间由调用方显式传入，便于精确断言边界。
 */
class SmsCodeStoreTest {

    private static final long NOW = Instant.parse("2024-01-01T00:00:00Z").toEpochMilli();
    private static final long TTL = 60_000L;

    private final InMemorySmsCodeStore store = new InMemorySmsCodeStore();

    @Test
    void savesAndConsumesOnce() {
        store.save("13800138000", "123456", NOW + TTL);

        assertTrue(store.hasValid("13800138000", NOW));
        assertTrue(store.verifyAndConsume("13800138000", "123456", NOW));
        assertFalse(store.hasValid("13800138000", NOW), "验证码用后即焚");
        assertFalse(store.verifyAndConsume("13800138000", "123456", NOW), "第二次必须失败");
    }

    @Test
    void wrongCodeDoesNotConsume() {
        store.save("13800138000", "123456", NOW + TTL);

        assertFalse(store.verifyAndConsume("13800138000", "654321", NOW));
        assertTrue(store.verifyAndConsume("13800138000", "123456", NOW), "错误尝试不应消耗正确验证码");
    }

    @Test
    void expiredCodeIsRejected() {
        store.save("13800138000", "123456", NOW + TTL);

        assertFalse(store.hasValid("13800138000", NOW + TTL + 1));
        assertFalse(store.verifyAndConsume("13800138000", "123456", NOW + TTL + 1));
    }

    @Test
    void sendingAgainReplacesPreviousCode() {
        store.save("13800138000", "123456", NOW + TTL);
        store.save("13800138000", "654321", NOW + TTL + 10_000);

        assertFalse(store.verifyAndConsume("13800138000", "123456", NOW + 1_000));
        assertTrue(store.verifyAndConsume("13800138000", "654321", NOW + 1_000));
    }

    @Test
    void loginAndResetScopesAreIsolated() {
        String phone = "13800138000";
        store.save("LOGIN", phone, "111111", NOW + TTL);
        store.save("RESET", phone, "222222", NOW + TTL);

        assertTrue(store.hasValid("RESET", phone, NOW), "找回密码验证码应独立存在");
        assertFalse(store.verifyAndConsume("RESET", phone, "111111", NOW), "登录码不能用于重置密码");
        assertFalse(store.verifyAndConsume("LOGIN", phone, "222222", NOW), "重置码不能用于登录");
        assertTrue(store.verifyAndConsume("LOGIN", phone, "111111", NOW));
        assertTrue(store.verifyAndConsume("RESET", phone, "222222", NOW));
    }

    @Test
    void verifyDoesNotConsumeCode() {
        String phone = "13800138000";
        store.save("RESET", phone, "123456", NOW + TTL);

        assertFalse(store.verify("RESET", phone, "654321", NOW), "错误验证码不通过");
        assertTrue(store.verify("RESET", phone, "123456", NOW), "正确验证码通过");
        assertTrue(store.verifyAndConsume("RESET", phone, "123456", NOW), "只校验不应消费验证码");
    }
}
