package com.sent.sports.auth.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sent.sports.auth.token.TokenService;
import com.sent.sports.common.web.CurrentUser;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 鉴权过滤器单元测试（P5-26）：令牌解析注入 CurrentUser、40102/40101 分流、
 * 剩余不足阈值下发 X-New-Token、ver 不匹配作废、白名单放行。
 */
class AuthFilterTest {

    private static final long AT_TTL = 2L * 60 * 60 * 1000;
    private static final long RENEW_THRESHOLD = 15L * 60 * 1000;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final SecretKey key = Keys.hmacShaKeyFor(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Set<String> whitelist = Set.of("/api/auth/login", "/api/auth/refresh", "/api/ping");

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(key, Clock.fixed(NOW, ZoneOffset.UTC), AT_TTL, RENEW_THRESHOLD);
    }

    /** SecurityContextHolder 是线程级全局状态，逐例清理避免污染其它测试。 */
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private AuthFilter filterWithVersion(long version) {
        return new AuthFilter(tokenService, (aud, userId) -> version, objectMapper, whitelist);
    }

    private String validToken(long ver) {
        return tokenService.issueAccess(new TokenService.Claims("user", 42L, ver, "jti-1")).accessToken();
    }

    @Test
    void validTokenInjectsCurrentUserAndProceeds() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader("Authorization", "Bearer " + validToken(3));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filterWithVersion(3).doFilter(req, res, chain);

        assertThat(chain.getRequest()).as("chain should proceed").isNotNull();
        CurrentUser cu = (CurrentUser) req.getAttribute(CurrentUser.ATTR);
        assertThat(cu).isNotNull();
        assertThat(cu.userId()).isEqualTo(42L);
        assertThat(cu.aud()).isEqualTo("user");
        assertThat(cu.jti()).isEqualTo("jti-1");
        // SecurityContext 闭环：principal 即 JWT 的 sub，details 里保留完整 CurrentUser
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("42");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getDetails())
                .isInstanceOf(CurrentUser.class)
                .isEqualTo(cu);
    }

    @Test
    void missingHeaderIsAnonymousAndProceeds() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/products");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filterWithVersion(0).doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(req.getAttribute(CurrentUser.ATTR)).isNull();
    }

    @Test
    void expiredTokenReturns401With40102() throws Exception {
        TokenService past = new TokenService(key, Clock.fixed(NOW.minusSeconds(3 * 3600), ZoneOffset.UTC),
                AT_TTL, RENEW_THRESHOLD);
        String expired = past.issueAccess(new TokenService.Claims("user", 42L, 0L, "j")).accessToken();

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader("Authorization", "Bearer " + expired);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filterWithVersion(0).doFilter(req, res, chain);

        assertThat(chain.getRequest()).as("chain must not proceed").isNull();
        assertThat(res.getStatus()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(res.getContentAsString());
        assertThat(body.path("code").asInt()).isEqualTo(40102);
    }

    @Test
    void invalidTokenReturns40101() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader("Authorization", "Bearer not-a-jwt");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filterWithVersion(0).doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(objectMapper.readTree(res.getContentAsString()).path("code").asInt()).isEqualTo(40101);
    }

    @Test
    void versionMismatchInvalidatesToken() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader("Authorization", "Bearer " + validToken(2));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filterWithVersion(5).doFilter(req, res, chain); // account bumped to 5, token says 2

        assertThat(chain.getRequest()).isNull();
        assertThat(res.getStatus()).isEqualTo(401);
        assertThat(objectMapper.readTree(res.getContentAsString()).path("code").asInt()).isEqualTo(40102);
    }

    @Test
    void issuesNewTokenWhenNearExpiry() throws Exception {
        TokenService shortTtl = new TokenService(key, Clock.fixed(NOW, ZoneOffset.UTC),
                10L * 60 * 1000, RENEW_THRESHOLD); // 10min < 15min threshold
        String nearExpiry = shortTtl.issueAccess(new TokenService.Claims("user", 42L, 1L, "j")).accessToken();

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/orders");
        req.addHeader("Authorization", "Bearer " + nearExpiry);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new AuthFilter(shortTtl, (aud, userId) -> 1L, objectMapper, whitelist).doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(res.getHeader("X-New-Token")).isNotBlank();
    }

    @Test
    void whitelistedPathSkipsParsing() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.addHeader("Authorization", "Bearer not-a-jwt");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filterWithVersion(0).doFilter(req, res, chain);

        assertThat(chain.getRequest()).as("whitelisted path must proceed").isNotNull();
    }
}
