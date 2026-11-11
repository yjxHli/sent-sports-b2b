package com.sent.sports.auth;

import com.sent.sports.auth.domain.UserAccount;
import com.sent.sports.auth.event.LoginAuditEvent;
import com.sent.sports.auth.repository.UserAccountRepository;
import com.sent.sports.auth.service.LoginUseCase;
import com.sent.sports.auth.service.dto.LoginCmd;
import com.sent.sports.auth.service.dto.TokenPair;
import com.sent.sports.auth.token.RefreshTokenPolicy;
import com.sent.sports.auth.token.RefreshTokenStore;
import com.sent.sports.auth.token.TokenService;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.event.DomainEventPublisher;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录用例测试（P3-60，TDD 红）：spec §安全 / 验收——
 * 密码错 5 次锁定 15 分钟、双令牌签发（access+refresh）、审计留痕（LoginAuditEvent）。
 * 此时 UserAccount/LoginUseCase 等尚未实现，编译即失败（红）。
 */
class LoginUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000_000L);

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private DomainEventPublisher eventPublisher;
    @Mock
    private java.time.Clock clock;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private RefreshTokenPolicy refreshTokenPolicy;
    @Mock
    private TokenService tokenService;
    @InjectMocks
    private LoginUseCase useCase;
    @Captor
    private ArgumentCaptor<UserAccount> accountCaptor;
    @Captor
    private ArgumentCaptor<LoginAuditEvent> eventCaptor;

    @Test
    void successIssuesDualTokensAndAudits() {
        UserAccount account = new UserAccount(1L, "alice", "secret");
        when(userAccountRepository.findByUsername("alice")).thenReturn(Optional.of(account));
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("RT")).thenReturn("RT0001");
        when(tokenService.issueAccess(any())).thenReturn(new TokenService.Issued("jwt-access", 9_000_000L));

        TokenPair r = useCase.login(new LoginCmd("alice", "secret"));

        assertEquals("jwt-access", r.accessToken());
        assertEquals("RT0001", r.refreshToken());
        verify(tokenService).issueAccess(new TokenService.Claims("admin", 1L, 0L, null));
        verify(userAccountRepository).save(accountCaptor.capture());
        assertEquals("RT0001", accountCaptor.getValue().getRefreshToken());
        verify(eventPublisher).publish(eventCaptor.capture());
        assertEquals(1L, eventCaptor.getValue().userId());
        verify(refreshTokenStore).save(eq("admin"), eq(1L), eq("default"),
                eq("RT0001"), anyLong());
    }

    @Test
    void customerLoginResolvesByPhone() {
        // 客户账号以手机号为登录标识：输入 11 位手机号时必须走 findByPhone，而不是 findByUsername。
        UserAccount account = new UserAccount(9002L, "13800138000", "13800138000", "123456");
        when(userAccountRepository.findByPhone("13800138000")).thenReturn(Optional.of(account));
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("RT")).thenReturn("RT0002");
        when(tokenService.issueAccess(any())).thenReturn(new TokenService.Issued("jwt-customer", 9_000_000L));

        TokenPair r = useCase.login(new LoginCmd("13800138000", "123456", "user", "device-x"));

        assertEquals("jwt-customer", r.accessToken());
        assertEquals("RT0002", r.refreshToken());
        verify(userAccountRepository).findByPhone("13800138000");
        verify(userAccountRepository, never()).findByUsername("13800138000");
        verify(tokenService).issueAccess(new TokenService.Claims("user", 9002L, 0L, null));
    }

    @Test
    void wrongPasswordThrowsAndIncrementsFailures() {
        UserAccount account = new UserAccount(1L, "alice", "secret");
        when(userAccountRepository.findByUsername("alice")).thenReturn(Optional.of(account));
        when(clock.instant()).thenReturn(NOW);

        BizException e = assertThrows(BizException.class,
                () -> useCase.login(new LoginCmd("alice", "wrong")));
        assertEquals(ErrorCode.BIZ_REJECT, e.getErrorCode());
        verify(userAccountRepository).save(accountCaptor.capture());
        assertEquals(1, accountCaptor.getValue().getFailedAttempts());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void locksAfterFiveFailures() {
        UserAccount account = new UserAccount(1L, "alice", "secret");
        when(userAccountRepository.findByUsername("alice")).thenReturn(Optional.of(account));
        when(clock.instant()).thenReturn(NOW);

        for (int i = 0; i < 4; i++) {
            assertThrows(BizException.class, () -> useCase.login(new LoginCmd("alice", "wrong")));
        }
        BizException e = assertThrows(BizException.class,
                () -> useCase.login(new LoginCmd("alice", "wrong"))); // 第 5 次
        assertEquals(ErrorCode.BIZ_REJECT, e.getErrorCode());
        assertEquals("LOCKED", account.getStatus());
        assertEquals(5, account.getFailedAttempts());
        assertEquals(NOW.toEpochMilli() + 15L * 60 * 1000, account.getLockUntilMillis());
    }

    @Test
    void unknownUserNotFound() {
        when(userAccountRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class,
                () -> useCase.login(new LoginCmd("ghost", "x")));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
    }
}
