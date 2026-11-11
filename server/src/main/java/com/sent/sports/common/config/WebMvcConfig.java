package com.sent.sports.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Web MVC 配置：跨域与敏感响应头暴露。
 * 令牌刷新/幂等等关键流程依赖前端读取以下响应头：
 * - {@code X-New-Token}：剩余 <15min 时下发的新 Access Token；
 * - {@code X-Token-Expire-In}：新令牌过期时间；
 * - {@code X-Idempotent-Replay}：幂等重放标记。
 *
 * <p>允许的来源由 {@code APP_CORS_ORIGINS} 环境变量注入，默认只放行本机两个 Vite dev server；
 * 生产部署时必须显式配置为真实前端域名，禁止使用 {@code *}（与 allowCredentials 冲突）。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String DEV_ORIGINS =
            "http://localhost:5173,http://localhost:5174,http://127.0.0.1:5173,http://127.0.0.1:5174";

    private static final String[] EXPOSE_HEADERS = {
            "X-New-Token", "X-Token-Expire-In", "X-Idempotent-Replay"
    };

    private final String[] allowedOrigins;

    public WebMvcConfig(@Value("${app.cors.allowed-origins:" + DEV_ORIGINS + "}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders(EXPOSE_HEADERS)
                .allowCredentials(true)
                .maxAge(3600);
    }
}
