package com.sent.sports.auth.service;

import com.sent.sports.auth.domain.UserAccount;
import com.sent.sports.auth.event.LoginAuditEvent;
import com.sent.sports.auth.repository.UserAccountRepository;
import com.sent.sports.auth.service.dto.LoginCmd;
import com.sent.sports.auth.service.dto.TokenPair;
import com.sent.sports.auth.token.RefreshTokenPolicy;
import com.sent.sports.auth.token.RefreshTokenStore;
import com.sent.sports.auth.token.TokenService;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.event.DomainEventPublisher;
import com.sent.sports.common.util.IdGen;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * 登录用例（P3-61 / P5-25 接入）：spec §安全 / 验收——
 * 密码错 5 次锁定 15 分钟、双令牌签发（access+refresh）、审计留痕（LoginAuditEvent）。
 * <p>P5-25：签发的 RT 同时登记到 {@link RefreshTokenStore}，按 {@code aud|userId|deviceId} 隔离，
 * 同设备重复登录会挤掉旧 RT（其他设备不受影响）。刷新链路只认该存储中的令牌，实现一次性轮换。
 * 仅依赖 auth 模块内部端口与 common，符合 ArchUnit 规则 4。
 */
@Service
public class LoginUseCase {

    /**
     * JWT 的 {@code ver} 声明：当前 {@code TokenVersionLookup} 对 user 恒返回 0、对 admin 读
     * {@code sys_staff.token_version}（种子账号为 0），而内存账号的 tokenVersion 只用于刷新令牌轮换。
     * 两者在此解耦，签发统一用 0，保证 AuthFilter 的版本校验闭环通过。
     */
    private static final long JWT_TOKEN_VERSION = 0L;

    /** 大陆手机号（客户账号登录标识）。 */
    private static final java.util.regex.Pattern PHONE_PATTERN =
            java.util.regex.Pattern.compile("^1[3-9]\\d{9}$");

    private final UserAccountRepository userAccountRepository;
    private final DomainEventPublisher eventPublisher;
    private final IdGen idGen;
    private final Clock clock;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenService tokenService;
    private final long refreshTtlMillis;

    public LoginUseCase(UserAccountRepository userAccountRepository, DomainEventPublisher eventPublisher,
                        IdGen idGen, Clock clock, RefreshTokenStore refreshTokenStore,
                        TokenService tokenService, RefreshTokenPolicy refreshTokenPolicy) {
        this.userAccountRepository = userAccountRepository;
        this.eventPublisher = eventPublisher;
        this.idGen = idGen;
        this.clock = clock;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenService = tokenService;
        long ttl = refreshTokenPolicy.ttlMillis();
        this.refreshTtlMillis = ttl > 0 ? ttl : RefreshTokenStore.DEFAULT_TTL_MILLIS;
    }

    @UseCase
    public TokenPair login(LoginCmd cmd) {
        UserAccount account = resolveAccount(cmd.username());
        long now = clock.instant().toEpochMilli();
        try {
            account.authenticate(cmd.password(), now);
        } catch (BizException e) {
            userAccountRepository.save(account); // 落库失败计数/锁定状态
            throw e;
        }
        // 双令牌：AT 必须是 TokenService 签发的真实 JWT（sub=userId, aud=user|admin），
        // 否则 AuthFilter 的 verify 会拒绝、前端也无法从 JWT 解析出 customerId。
        String refreshToken = idGen.next("RT");
        account.rotateToken(refreshToken);
        userAccountRepository.save(account);
        refreshTokenStore.save(cmd.normalizedAud(), account.getUserId(), cmd.normalizedDeviceId(),
                refreshToken, now + refreshTtlMillis);
        eventPublisher.publish(new LoginAuditEvent(account.getUserId(), true, now));
        TokenService.Issued issued = tokenService.issueAccess(new TokenService.Claims(
                cmd.normalizedAud(), account.getUserId(), JWT_TOKEN_VERSION, null));
        return new TokenPair(issued.accessToken(), refreshToken);
    }

    /**
     * 解析登录主体：输入形如大陆手机号时优先按 {@code phone} 查客户账号，
     * 否则按 {@code username} 查员工/客服账号；手机号查不到时回退到 username
     * （兼容历史以手机号作为用户名的账号）。
     */
    private UserAccount resolveAccount(String login) {
        if (login != null && PHONE_PATTERN.matcher(login).matches()) {
            UserAccount byPhone = userAccountRepository.findByPhone(login).orElse(null);
            if (byPhone != null) {
                return byPhone;
            }
        }
        return userAccountRepository.findByUsername(login)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
    }
}
