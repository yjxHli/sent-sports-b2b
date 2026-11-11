package com.sent.sports.common.ratelimit;

import java.time.Duration;

/**
 * 限流端口（P5-30）：滑动窗口限流能力抽象。
 * <p>拦截器只依赖此接口（低耦合），真实实现（如 Redis ZSET 滑动窗口）由基础设施层提供，
 * 单测可用伪造实现替换。</p>
 */
public interface RateLimiter {

    /**
     * 尝试为某键在窗口内占用一次配额。
     *
     * @param key    限流键（rate:{principalId}:{path}）
     * @param limit  窗口内允许的最大次数
     * @param window 窗口长度
     * @return 未超限返回 true；已超限返回 false
     */
    boolean tryAcquire(String key, int limit, Duration window);
}
