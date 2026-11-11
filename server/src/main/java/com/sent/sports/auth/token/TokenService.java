package com.sent.sports.auth.token;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;

/**
 * 双令牌之 Access Token 服务（P5-24）。
 * <p>JWT 声明：{@code sub}=userId，{@code aud}=user|admin，{@code jti}=唯一标识（吊销/续期链），
 * {@code ver}=token_version（改密/强制下线 +1 后旧令牌失效），{@code iat}/{@code exp}。
 * 有效期 2h；滑动续期在剩余 &lt; 阈值（默认 15min）时由 {@link #shouldRenew(long)} 判定，
 * 续期令牌复用同一 {@code jti} 并顺延 {@code exp}（plan §7.11.2）。</p>
 * <p>为便于单测，过期判定使用注入的 {@link Clock}（桥接 jjwt 的 clock），而非系统真实时间。</p>
 */
public class TokenService {

    public static final String AUD_USER = "user";
    public static final String AUD_ADMIN = "admin";

    private static final String CLAIM_VER = "ver";

    private final SecretKey key;
    private final Clock clock;
    private final long accessTtlMillis;
    private final long renewThresholdMillis;

    public TokenService(SecretKey key, Clock clock, long accessTtlMillis, long renewThresholdMillis) {
        this.key = key;
        this.clock = clock;
        this.accessTtlMillis = accessTtlMillis;
        this.renewThresholdMillis = renewThresholdMillis;
    }

    /** 签发入参：aud + 用户 + token_version + 可复用的 jti（续期时传旧 jti）。 */
    public record Claims(String aud, long userId, long tokenVersion, String jti) {
    }

    /** 签发结果：令牌串 + 过期时间戳（epoch millis）。 */
    public record Issued(String accessToken, long expireAtMillis) {
    }

    /** 校验结果：解码后的声明。 */
    public record Verified(String aud, long userId, long tokenVersion, String jti, long expireAtMillis) {
    }

    /** 签发 Access Token。jti 为空则生成新的 UUID。 */
    public Issued issueAccess(Claims claims) {
        long now = clock.instant().toEpochMilli();
        long exp = now + accessTtlMillis;
        String jti = (claims.jti() == null || claims.jti().isBlank()) ? UUID.randomUUID().toString() : claims.jti();
        String token = Jwts.builder()
                .subject(Long.toString(claims.userId()))
                .audience().add(claims.aud()).and()
                .id(jti)
                .claim(CLAIM_VER, claims.tokenVersion())
                .issuedAt(new Date(now))
                .expiration(new Date(exp))
                .signWith(key)
                .compact();
        return new Issued(token, exp);
    }

    /**
     * 校验令牌：过期 → {@code TOKEN_EXPIRED}（40102）；签名/格式错误 → {@code UNAUTHENTICATED}（40101）。
     * aud 与 ver 的业务判定由 {@code AuthFilter} 结合当前账号完成。
     */
    public Verified verify(String token) {
        try {
            io.jsonwebtoken.Claims body = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String aud = body.getAudience().isEmpty() ? null : body.getAudience().iterator().next();
            long userId = Long.parseLong(body.getSubject());
            long ver = ((Number) body.get(CLAIM_VER)).longValue();
            return new Verified(aud, userId, ver, body.getId(), body.getExpiration().getTime());
        } catch (ExpiredJwtException e) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.UNAUTHENTICATED);
        }
    }

    /** 剩余有效期在 (0, 阈值) 内时返回 true，触发滑动续期下发 X-New-Token。 */
    public boolean shouldRenew(long expireAtMillis) {
        long remaining = expireAtMillis - clock.instant().toEpochMilli();
        return remaining > 0 && remaining < renewThresholdMillis;
    }
}
