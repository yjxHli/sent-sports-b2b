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

/** 刷新与登出用例（2026-11-18 认证重构）：RT 一次性轮换 + 按 aud/id 从数据库回读账号。 */
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

    private RefreshTokenStore.Session session(String aud, long userId) {
        return new RefreshTokenStore.Session(aud, userId, "default", NOW.toEpochMilli() + 7_000_000L);
    }

    @Test
    void refreshRotatesTokenAndBlacklistsOld() {
        UserAccount account = new UserAccount(1L, "alice", "secret");
        when(logoutTokenRepository.exists("RT0001")).thenReturn(false);
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("RT")).thenReturn("RT0002");
        when(refreshTokenStore.rotate(anyString(), anyString(), anyLong())).thenReturn(Optional.of(session("admin", 1L)));
        when(userAccountRepository.findById("admin", 1L)).thenReturn(Optional.of(account));
        when(tokenService.issueAccess(any())).thenReturn(new TokenService.Issued("jwt-access-2", 9_000_000L));

        TokenPair r = useCase.refresh(new RefreshCmd("RT0001"));

        assertEquals("jwt-access-2", r.accessToken());
        assertEquals("RT0002", r.refreshToken());
        verify(refreshTokenStore).rotate("RT0001", "RT0002",
                NOW.toEpochMilli() + RefreshTokenStore.DEFAULT_TTL_MILLIS);
        verify(userAccountRepository).findById("admin", 1L);
        verify(userAccountRepository).save(account);
        assertEquals("RT0002", account.getRefreshToken());
        verify(logoutTokenRepository).save(blacklistCaptor.capture());
        assertEquals("RT0001", blacklistCaptor.getValue().token());
    }

    @Test
    void refreshFailsForUnknownToken() {
        when(logoutTokenRepository.exists("X")).thenReturn(false);
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("RT")).thenReturn("RT0002");
        when(refreshTokenStore.rotate(anyString(), anyString(), anyLong())).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class,
                () -> useCase.refresh(new RefreshCmd("X")));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, e.getErrorCode());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void refreshFailsForBlacklistedToken() {
        when(logoutTokenRepository.exists("RT0001")).thenReturn(true);

        BizException e = assertThrows(BizException.class,
                () -> useCase.refresh(new RefreshCmd("RT0001")));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, e.getErrorCode());
        verify(refreshTokenStore, never()).rotate(anyString(), anyString(), anyLong());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void refreshFailsWhenAccountMissing() {
        when(logoutTokenRepository.exists("RT0001")).thenReturn(false);
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("RT")).thenReturn("RT0002");
        when(refreshTokenStore.rotate(anyString(), anyString(), anyLong())).thenReturn(Optional.of(session("admin", 1L)));
        when(userAccountRepository.findById("admin", 1L)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class,
                () -> useCase.refresh(new RefreshCmd("RT0001")));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, e.getErrorCode());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void refreshFailsWhenAccountLocked() {
        UserAccount account = UserAccount.rehydrate(1L, "admin", "alice", null, null, "secret",
                UserAccount.LOCKED, 5, NOW.toEpochMilli() + 1000, 0L);
        when(logoutTokenRepository.exists("RT0001")).thenReturn(false);
        when(clock.instant()).thenReturn(NOW);
        when(idGen.next("RT")).thenReturn("RT0002");
        when(refreshTokenStore.rotate(anyString(), anyString(), anyLong())).thenReturn(Optional.of(session("admin", 1L)));
        when(userAccountRepository.findById("admin", 1L)).thenReturn(Optional.of(account));

        BizException e = assertThrows(BizException.class,
                () -> useCase.refresh(new RefreshCmd("RT0001")));
        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, e.getErrorCode());
        verify(userAccountRepository, never()).save(any());
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
