package com.sent.sports.common.interceptor;

import com.sent.sports.common.anno.RateLimit;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.ratelimit.RateLimiter;
import com.sent.sports.common.web.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 限流拦截器（P5-30）：读取方法上的 {@link RateLimit}，滑块窗口内超限返回 429 + {@code Retry-After}。
 * <p>键 {@code rate:{principalId}:{path}}：已登录用 userId，未登录用来源 IP。
 * 仅协调限流端口 {@link RateLimiter}，不关心其底层存储（Redis/内存），高内聚低耦合。</p>
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "rate:";

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit annotation = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (annotation == null) {
            return true;
        }
        String key = KEY_PREFIX + principalOf(request) + ":" + request.getRequestURI();
        Duration window = Duration.ofSeconds(annotation.windowSeconds());
        if (!rateLimiter.tryAcquire(key, annotation.limit(), window)) {
            response.setHeader("Retry-After", String.valueOf(annotation.windowSeconds()));
            throw new BizException(ErrorCode.TOO_FREQUENT);
        }
        return true;
    }

    private String principalOf(HttpServletRequest request) {
        Object attr = request.getAttribute(CurrentUser.ATTR);
        if (attr instanceof CurrentUser user) {
            return String.valueOf(user.userId());
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "anonymous" : remote;
    }
}
