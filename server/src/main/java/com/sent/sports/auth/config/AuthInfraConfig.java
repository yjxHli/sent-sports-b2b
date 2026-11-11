package com.sent.sports.auth.config;

import com.sent.sports.auth.token.RefreshTokenPolicy;
import com.sent.sports.auth.token.TokenService;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.SecretKey;

/** 鉴权基础设施装配：JWT 签名密钥与有效期（生产用环境变量注入，勿硬编码）。 */
@Configuration
public class AuthInfraConfig {

    @Bean
    public TokenService tokenService(@Value("${app.jwt.secret}") String secret,
                                     Clock clock,
                                     @Value("${app.jwt.access-ttl-ms:7200000}") long accessTtlMillis,
                                     @Value("${app.jwt.renew-threshold-ms:900000}") long renewThresholdMillis) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return new TokenService(key, clock, accessTtlMillis, renewThresholdMillis);
    }

    /** 刷新令牌有效期（默认 7 天）：供登录签发与刷新轮换共用，避免两处 TTL 漂移。 */
    @Bean
    public RefreshTokenPolicy refreshTokenPolicy(
            @Value("${app.jwt.refresh-ttl-ms:604800000}") long refreshTtlMillis) {
        return new RefreshTokenPolicy(refreshTtlMillis);
    }
}
