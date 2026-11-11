package com.sent.sports.common.interceptor;

import com.sent.sports.common.anno.RateLimit;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.ratelimit.RateLimiter;
import com.sent.sports.common.web.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 限流拦截器单元测试（P5-30）：命中注解才限流、超限 429 + Retry-After、按用户/路径建键。
 * 通过伪造 {@link RateLimiter} 端口验证协调逻辑，不依赖真实 Redis。
 */
class RateLimitInterceptorTest {

    /** 测试用控制器：一个受限流注解的方法 + 一个不限流的方法。 */
    static class DummyController {
        @RateLimit(limit = 2, windowSeconds = 30)
        public void limited() {
        }

        public void unlimited() {
        }
    }

    /** 伪造限流端口：记录入参并返回可控结果。 */
    static class FakeRateLimiter implements RateLimiter {
        boolean allow = true;
        boolean called = false;
        String key;
        int limit;
        Duration window;

        @Override
        public boolean tryAcquire(String key, int limit, Duration window) {
            this.called = true;
            this.key = key;
            this.limit = limit;
            this.window = window;
            return allow;
        }
    }

    private HandlerMethod handlerOf(String methodName) throws NoSuchMethodException {
        Method m = DummyController.class.getMethod(methodName);
        return new HandlerMethod(new DummyController(), m);
    }

    @Test
    void allowsWhenLimiterAccepts() throws Exception {
        FakeRateLimiter limiter = new FakeRateLimiter();
        RateLimitInterceptor interceptor = new RateLimitInterceptor(limiter);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(req, res, handlerOf("limited"));

        assertThat(allowed).isTrue();
        assertThat(limiter.called).isTrue();
        assertThat(limiter.key).isEqualTo("rate:1.2.3.4:/api/auth/login");
        assertThat(limiter.limit).isEqualTo(2);
        assertThat(limiter.window).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void rejectsWith429WhenLimiterDenies() throws Exception {
        FakeRateLimiter limiter = new FakeRateLimiter();
        limiter.allow = false;
        RateLimitInterceptor interceptor = new RateLimitInterceptor(limiter);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr("1.2.3.4");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(req, res, handlerOf("limited")))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.TOO_FREQUENT));
        assertThat(res.getHeader("Retry-After")).isEqualTo("30");
    }

    @Test
    void skipsWhenNoAnnotation() throws Exception {
        FakeRateLimiter limiter = new FakeRateLimiter();
        RateLimitInterceptor interceptor = new RateLimitInterceptor(limiter);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(req, res, handlerOf("unlimited"));

        assertThat(allowed).isTrue();
        assertThat(limiter.called).isFalse();
    }

    @Test
    void usesCurrentUserIdAsPrincipalWhenPresent() throws Exception {
        FakeRateLimiter limiter = new FakeRateLimiter();
        RateLimitInterceptor interceptor = new RateLimitInterceptor(limiter);
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/orders");
        req.setAttribute(CurrentUser.ATTR, new CurrentUser("user", 99L, "j"));
        MockHttpServletResponse res = new MockHttpServletResponse();

        interceptor.preHandle(req, res, handlerOf("limited"));

        assertThat(limiter.key).isEqualTo("rate:99:/api/orders");
    }

    @Test
    void skipsNonHandlerMethod() throws Exception {
        FakeRateLimiter limiter = new FakeRateLimiter();
        RateLimitInterceptor interceptor = new RateLimitInterceptor(limiter);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/ping");
        MockHttpServletResponse res = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(req, res, new Object());

        assertThat(allowed).isTrue();
        assertThat(limiter.called).isFalse();
    }
}
