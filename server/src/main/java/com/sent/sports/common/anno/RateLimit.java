package com.sent.sports.common.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解（P5-30）：标注在 Controller 方法上，声明滑动窗口内允许的最大请求数。
 * 由 {@code RateLimitInterceptor} 读取并执行，键为 {@code rate:{principalId}:{path}}。
 * 未标注的方法不限流。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 窗口内允许的最大请求数。 */
    int limit() default 60;

    /** 窗口长度（秒）。 */
    int windowSeconds() default 60;
}
