package com.sent.sports.auth.token;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 令牌服务单元测试（P5-24）：JWT 签发/校验/过期分流/滑动续期判定。
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final long AT_TTL = 2L * 60 * 60 * 1000;        // 2h
    private static final long RENEW_THRESHOLD = 15L * 60 * 1000;    // 15min
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private java.time.Clock clock;

    private final SecretKey key = Keys.hmacShaKeyFor(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(key, clock, AT_TTL, RENEW_THRESHOLD);
    }

    @Test
    void issuedTokenVerifiesWithClaims() {
        when(clock.instant()).thenReturn(NOW);
        TokenService.Claims claims = new TokenService.Claims("user", 42L, 3L, "jti-1");
        TokenService.Issued issued = tokenService.issueAccess(claims);

        assertThat(issued.accessToken()).isNotBlank();
        assertThat(issued.expireAtMillis()).isEqualTo(NOW.toEpochMilli() + AT_TTL);

        TokenService.Verified v = tokenService.verify(issued.accessToken());
        assertThat(v.aud()).isEqualTo("user");
        assertThat(v.userId()).isEqualTo(42L);
        assertThat(v.tokenVersion()).isEqualTo(3L);
        assertThat(v.jti()).isEqualTo("jti-1");
        assertThat(v.expireAtMillis()).isEqualTo(issued.expireAtMillis());
    }

    @Test
    void expiredTokenThrowsTokenExpired() {
        when(clock.instant()).thenReturn(NOW, NOW.plusMillis(AT_TTL + 1));
        TokenService.Issued issued = tokenService.issueAccess(
                new TokenService.Claims("user", 1L, 0L, "jti-x"));

        assertThatThrownBy(() -> tokenService.verify(issued.accessToken()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.TOKEN_EXPIRED));
    }

    @Test
    void tamperedTokenThrowsUnauthenticated() {
        when(clock.instant()).thenReturn(NOW);
        TokenService.Issued issued = tokenService.issueAccess(
                new TokenService.Claims("user", 1L, 0L, "jti-y"));

        String tampered = issued.accessToken() + "x";
        assertThatThrownBy(() -> tokenService.verify(tampered))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void wrongKeyThrowsUnauthenticated() {
        when(clock.instant()).thenReturn(NOW);
        TokenService.Issued issued = tokenService.issueAccess(
                new TokenService.Claims("admin", 7L, 1L, "jti-z"));

        SecretKey otherKey = Keys.hmacShaKeyFor(
                "ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8));
        TokenService other = new TokenService(otherKey, clock, AT_TTL, RENEW_THRESHOLD);
        assertThatThrownBy(() -> other.verify(issued.accessToken()))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }

    @Test
    void shouldRenewWhenRemainingBelowThreshold() {
        when(clock.instant()).thenReturn(NOW);
        long now = NOW.toEpochMilli();
        assertThat(tokenService.shouldRenew(now + 10L * 60 * 1000)).isTrue();   // 10min left
        assertThat(tokenService.shouldRenew(now + 20L * 60 * 1000)).isFalse();  // 20min left
        assertThat(tokenService.shouldRenew(now - 1)).isFalse();                 // already expired
    }

    @Test
    void renewKeepsSameJti() {
        when(clock.instant()).thenReturn(NOW, NOW.plusMillis(1000));
        TokenService.Issued first = tokenService.issueAccess(
                new TokenService.Claims("user", 5L, 2L, "stable-jti"));
        TokenService.Verified v = tokenService.verify(first.accessToken());
        TokenService.Issued renewed = tokenService.issueAccess(
                new TokenService.Claims(v.aud(), v.userId(), v.tokenVersion(), v.jti()));
        assertThat(tokenService.verify(renewed.accessToken()).jti()).isEqualTo("stable-jti");
    }
}
