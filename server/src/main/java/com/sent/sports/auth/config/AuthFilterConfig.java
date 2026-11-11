package com.sent.sports.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.auth.filter.AuthFilter;
import com.sent.sports.auth.token.TokenService;
import com.sent.sports.auth.token.TokenVersionLookup;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * 鉴权过滤器装配（P5-26 收尾）：把 {@link AuthFilter} 注册到 {@code /api/*}。
 * <p>此前只有过滤器类、没有注册，全量上下文加载后所有请求都拿不到 {@code CurrentUser}，
 * 权限拦截器和数据范围拦截器自然也不会生效——这是「核心链路跑通」的必要一环。
 * WebSocket 握手不走本过滤器（URL 不在 /api/*），由 {@code ChatWebSocketHandler} 自行校验 query 中的令牌。
 */
@Configuration
public class AuthFilterConfig {

    /** 公开路径前缀：登录/刷新/登出、健康检查、接口文档。 */
    private static final Set<String> WHITELIST = Set.of(
            "/api/auth/", "/api/ping", "/actuator/", "/v3/api-docs", "/swagger-ui");

    /**
     * 过滤器实例交给 Spring Security 链承载（见 {@code SecurityConfig#addFilterAfter}），
     * 这样 AuthFilter 写入的 SecurityContext 不会被 SecurityContextHolderFilter 覆盖。
     */
    @Bean
    public AuthFilter authFilter(TokenService tokenService,
                                 TokenVersionLookup versionLookup,
                                 ObjectMapper objectMapper) {
        return new AuthFilter(tokenService, versionLookup, objectMapper, WHITELIST);
    }

    /**
     * Spring Boot 会自动把 {@link AuthFilter} 这个 Filter Bean 注册到 Servlet 容器，
     * 导致同一请求执行两次（且第一次写入的 SecurityContext 被清理）。这里显式禁用独立注册，
     * 只保留 Security 链中的那一次执行。
     */
    @Bean
    public FilterRegistrationBean<AuthFilter> authFilterRegistration(AuthFilter filter) {
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        registration.setName("authFilter");
        return registration;
    }
}
