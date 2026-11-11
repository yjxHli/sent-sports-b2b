package com.sent.sports.common.config;

import com.sent.sports.common.idempotent.IdempotencyStore;
import com.sent.sports.common.interceptor.DataScopeWebInterceptor;
import com.sent.sports.common.interceptor.IdempotentInterceptor;
import com.sent.sports.common.interceptor.PermissionInterceptor;
import com.sent.sports.common.interceptor.RateLimitInterceptor;
import com.sent.sports.common.ratelimit.RateLimiter;
import com.sent.sports.common.web.DataScopeResolver;
import com.sent.sports.common.web.PermissionChecker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 拦截器装配（P5-27/28/29/30 统一入口）。
 * <p>用 {@link ObjectProvider} 而非直接注入依赖：{@code @WebMvcTest} 切片只会加载少量 Bean，
 * 缺少对应端口时跳过注册，契约测试仍能正常启动（这也是 {@code addFilters=false} 之外的第二重保护）。
 * 顺序：限流 → 幂等 → 权限 → 数据范围；数据范围最后设置、最先清理（afterCompletion 反序）。
 */
@Configuration
public class WebInterceptorConfig implements WebMvcConfigurer {

    private final ObjectProvider<RateLimiter> rateLimiter;
    private final ObjectProvider<IdempotencyStore> idempotencyStore;
    private final ObjectProvider<PermissionChecker> permissionChecker;
    private final ObjectProvider<DataScopeResolver> dataScopeResolver;

    public WebInterceptorConfig(ObjectProvider<RateLimiter> rateLimiter,
                                ObjectProvider<IdempotencyStore> idempotencyStore,
                                ObjectProvider<PermissionChecker> permissionChecker,
                                ObjectProvider<DataScopeResolver> dataScopeResolver) {
        this.rateLimiter = rateLimiter;
        this.idempotencyStore = idempotencyStore;
        this.permissionChecker = permissionChecker;
        this.dataScopeResolver = dataScopeResolver;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        RateLimiter limiter = rateLimiter.getIfAvailable();
        if (limiter != null) {
            registry.addInterceptor(new RateLimitInterceptor(limiter)).addPathPatterns("/api/**");
        }
        IdempotencyStore store = idempotencyStore.getIfAvailable();
        if (store != null) {
            registry.addInterceptor(new IdempotentInterceptor(store)).addPathPatterns("/api/**");
        }
        PermissionChecker checker = permissionChecker.getIfAvailable();
        if (checker != null) {
            registry.addInterceptor(new PermissionInterceptor(checker)).addPathPatterns("/api/**");
        }
        DataScopeResolver resolver = dataScopeResolver.getIfAvailable();
        if (resolver != null) {
            registry.addInterceptor(new DataScopeWebInterceptor(resolver)).addPathPatterns("/api/admin/**");
        }
    }
}
