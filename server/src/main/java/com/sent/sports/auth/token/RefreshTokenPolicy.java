package com.sent.sports.auth.token;

/**
 * 刷新令牌有效期策略（P5-25）。
 * <p>把配置值封装成对象而非在用例构造器里直接注入 {@code long}：
 * 一是保持构造器注入且可由 Spring 统一装配；二是用例单测能直接 mock 该依赖。
 * 配置为非正数时由用例回落到 {@link RefreshTokenStore#DEFAULT_TTL_MILLIS}。
 */
public final class RefreshTokenPolicy {

    private final long ttlMillis;

    public RefreshTokenPolicy(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    public long ttlMillis() {
        return ttlMillis;
    }
}
