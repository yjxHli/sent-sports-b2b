package com.sent.sports.auth.service;

import com.sent.sports.auth.domain.LogoutToken;
import com.sent.sports.auth.domain.UserAccount;
import com.sent.sports.auth.repository.LogoutTokenRepository;
import com.sent.sports.auth.repository.UserAccountRepository;
import com.sent.sports.auth.service.dto.LogoutCmd;
import com.sent.sports.auth.service.dto.RefreshCmd;
import com.sent.sports.auth.service.dto.TokenPair;
import com.sent.sports.auth.token.RefreshTokenPolicy;
import com.sent.sports.auth.token.RefreshTokenStore;
import com.sent.sports.auth.token.TokenService;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;

import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * 刷新与登出用例（P3-63）：spec §安全 / 验收 A12——
 * 刷新令牌一次性轮换（旧 RT 失效并拉黑）、token_version 随轮换自增、登出令牌列入黑名单。
 * <p>P5-25：{@link RefreshTokenStore} 是 RT 的权威索引，{@code rotate} 原子完成
 * 「校验旧令牌 → 用后即焚 → 写新令牌」，因此同一 RT 并发刷新只有一个能成功；
 * 旧的黑名单机制保留作纵深防御（历史令牌 / 进程内存储重启后仍安全）。
 * 仅依赖 auth 模块内部仓储端口与 common，符合 ArchUnit 规则 4。
 */
@Service
public class RefreshTokenUseCase {

    private static final long JWT_TOKEN_VERSION = 0L;

    private final UserAccountRepository userAccountRepository;
    private final LogoutTokenRepository logoutTokenRepository;
    private final IdGen idGen;
    private final Clock clock;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenService tokenService;
    private final long refreshTtlMillis;

    public RefreshTokenUseCase(UserAccountRepository userAccountRepository, LogoutTokenRepository logoutTokenRepository,
                              IdGen idGen, Clock clock, RefreshTokenStore refreshTokenStore,
                              TokenService tokenService, RefreshTokenPolicy refreshTokenPolicy) {
        this.userAccountRepository = userAccountRepository;
        this.logoutTokenRepository = logoutTokenRepository;
        this.idGen = idGen;
        this.clock = clock;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenService = tokenService;
        long ttl = refreshTokenPolicy.ttlMillis();
        this.refreshTtlMillis = ttl > 0 ? ttl : RefreshTokenStore.DEFAULT_TTL_MILLIS;
    }

    @UseCase
    public TokenPair refresh(RefreshCmd cmd) {
        UserAccount account = userAccountRepository.findByRefreshToken(cmd.refreshToken())
                .orElseThrow(() -> new BizException(ErrorCode.REFRESH_TOKEN_INVALID));
        if (logoutTokenRepository.exists(cmd.refreshToken())) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID); // 已登出/已用
        }
        long now = clock.instant().toEpochMilli();
        if (account.isLocked(now)) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        String oldRefreshToken = account.getRefreshToken();
        String refreshToken = idGen.next("RT");
        // 权威一次性轮换：不在存储中的 RT（重放 / 已登出 / 已过期 / 应用重启）一律拒绝
        RefreshTokenStore.Session session = refreshTokenStore.rotate(
                        oldRefreshToken == null ? cmd.refreshToken() : oldRefreshToken,
                        refreshToken, now + refreshTtlMillis)
                .orElseThrow(() -> new BizException(ErrorCode.REFRESH_TOKEN_INVALID));
        if (session.userId() != account.getUserId()) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID); // 令牌与账号绑定不一致
        }
        account.rotateToken(refreshToken);
        userAccountRepository.save(account);
        logoutTokenRepository.save(new LogoutToken(oldRefreshToken, now)); // 旧 RT 失效
        TokenService.Issued issued = tokenService.issueAccess(new TokenService.Claims(
                session.aud(), account.getUserId(), JWT_TOKEN_VERSION, null));
        return new TokenPair(issued.accessToken(), refreshToken);
    }

    @UseCase
    public void logout(LogoutCmd cmd) {
        long now = clock.instant().toEpochMilli();
        logoutTokenRepository.save(new LogoutToken(cmd.refreshToken(), now));
        refreshTokenStore.revoke(cmd.refreshToken());
    }

    /** 改密 / 强制下线：一次性撤销某登录端某用户的所有设备（P5-25 端口能力）。 */
    public void revokeAll(String aud, long userId) {
        refreshTokenStore.revokeAll(aud, userId);
    }
}
