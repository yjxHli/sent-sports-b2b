package com.sent.sports.auth;

import com.sent.sports.auth.domain.LogoutToken;
import com.sent.sports.auth.domain.UserAccount;
import com.sent.sports.auth.repository.LogoutTokenRepository;
import com.sent.sports.auth.repository.UserAccountRepository;
import com.sent.sports.auth.service.RefreshTokenUseCase;
import com.sent.sports.auth.service.dto.LogoutCmd;
import com.sent.sports.auth.service.dto.RefreshCmd;
import com.sent.sports.auth.service.dto.TokenPair;
import com.sent.sports.auth.token.RefreshTokenPolicy;
import com.sent.sports.auth.token.RefreshTokenStore;
import com.sent.sports.auth.token.TokenService;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 刷新与登出用例测试（P3-62，TDD 红）：spec §安全 / 验收 A12——
 * RT 一次性轮换、旧 RT 失效、token_version 失效、登出列入黑名单。
 * 此时 RefreshTokenUseCase 等尚未实现，编译即失败（红）。
 */
class RefreshTokenUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000_000L);

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private LogoutTokenRepository logoutTokenRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private java.time.Clock clock;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private RefreshTokenPolicy refreshTokenPolicy;
    @Mock
    private TokenService tokenService;
    @InjectMocks
    private RefreshTokenUseCase useCase;
    @Captor
    private ArgumentCaptor<LogoutToken> blacklistCaptor;
    @Captor
    private ArgumentCaptor<UserAccount> accountCaptor;

    @Test
    void refreshRotatesTokenAndBlacklistsOld() {
        UserAccount account = new UserAccount(1L, "alice", "secret");
        account.setRefreshToken("RT0001");
        when(userAccountRepository.findByRefreshToken("RT0001")).thenReturn(Optional.of(account));
        when(logoutTokenRepository.exists("RT0001")).thenReturn(false);
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("RT")).thenReturn("RT0002");
        when(refreshTokenStore.rotate(anyString(), anyString(), anyLong())).thenReturn(
                Optional.of(new RefreshTokenStore.Session("admin", 1L, "default", NOW.toEpochMilli() + 7_000_000L)));
        when(tokenService.issueAccess(any())).thenReturn(new TokenService.Issued("jwt-access-2", 9_000_000L));

        TokenPair r = useCase.refresh(new RefreshCmd("RT0001"));

        assertEquals("jwt-access-2", r.accessToken());
        assertEquals("RT0002", r.refreshToken());
        verify(refreshTokenStore).rotate("RT0001", "RT0002",
                NOW.toEpochMilli() + RefreshTokenStore.DEFAULT_TTL_MILLIS);
        verify(userAccountRepository).save(accountCaptor.capture());
        assertEquals("RT0002", accountCaptor.getValue().getRefreshToken());
        verify(logoutTokenRepository).save(blacklistCaptor.capture());
        assertEquals("RT0001", blacklistCaptor.getValue().token());
    }

    @Test
    void refreshFailsForUnknownToken() {
        when(userAccountRepository.findByRefreshToken("X")).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class,
                () -> useCase.refresh(new RefreshCmd("X")));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, e.getErrorCode());
    }

    @Test
    void refreshFailsForBlacklistedToken() {
        UserAccount account = new UserAccount(1L, "alice", "secret");
        account.setRefreshToken("RT0001");
        when(userAccountRepository.findByRefreshToken("RT0001")).thenReturn(Optional.of(account));
        when(logoutTokenRepository.exists("RT0001")).thenReturn(true);

        BizException e = assertThrows(BizException.class,
                () -> useCase.refresh(new RefreshCmd("RT0001")));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, e.getErrorCode());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void refreshFailsWhenStoreRejectsReplayedToken() {
        UserAccount account = new UserAccount(1L, "alice", "secret");
        account.setRefreshToken("RT0001");
        when(userAccountRepository.findByRefreshToken("RT0001")).thenReturn(Optional.of(account));
        when(logoutTokenRepository.exists("RT0001")).thenReturn(false);
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("RT")).thenReturn("RT0002");
        when(refreshTokenStore.rotate(anyString(), anyString(), anyLong())).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class,
                () -> useCase.refresh(new RefreshCmd("RT0001")));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, e.getErrorCode());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void refreshFailsWhenSessionDoesNotMatchAccount() {
        UserAccount account = new UserAccount(1L, "alice", "secret");
        account.setRefreshToken("RT0001");
        when(userAccountRepository.findByRefreshToken("RT0001")).thenReturn(Optional.of(account));
        when(logoutTokenRepository.exists("RT0001")).thenReturn(false);
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("RT")).thenReturn("RT0002");
        when(refreshTokenStore.rotate(anyString(), anyString(), anyLong())).thenReturn(
                Optional.of(new RefreshTokenStore.Session("admin", 99L, "default", NOW.toEpochMilli() + 7_000_000L)));

        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID,
                assertThrows(BizException.class, () -> useCase.refresh(new RefreshCmd("RT0001"))).getErrorCode());
    }

    @Test
    void logoutBlacklistsToken() {
        when(clock.instant()).thenReturn(NOW);

        useCase.logout(new LogoutCmd("RT0001"));

        verify(logoutTokenRepository).save(blacklistCaptor.capture());
        assertEquals("RT0001", blacklistCaptor.getValue().token());
        verify(refreshTokenStore).revoke("RT0001");
    }

    @Test
    void revokeAllDelegatesToStore() {
        useCase.revokeAll("admin", 42L);

        verify(refreshTokenStore).revokeAll("admin", 42L);
    }
}
