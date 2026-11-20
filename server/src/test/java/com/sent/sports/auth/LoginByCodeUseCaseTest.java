package com.sent.sports.auth;

import com.sent.sports.auth.domain.UserAccount;
import com.sent.sports.auth.repository.SmsCodeStore;
import com.sent.sports.auth.repository.UserAccountRepository;
import com.sent.sports.auth.service.LoginByCodeUseCase;
import com.sent.sports.auth.service.LoginUseCase;
import com.sent.sports.auth.service.dto.TokenPair;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证码登录用例测试（TDD 红）：验证码正确才查账号、未注册手机号专用拦截码、验证码一次性。
 */
class LoginByCodeUseCaseTest extends BaseUnitTest {

    private static final String PHONE = "13800138000";
    private static final String CODE = "123456";
    private static final long NOW = FIXED_INSTANT.toEpochMilli();

    @Mock
    private SmsCodeStore smsCodeStore;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private LoginUseCase loginUseCase;
    @Mock
    private java.time.Clock clock;
    @InjectMocks
    private LoginByCodeUseCase useCase;

    @Test
    void validCodeIssuesTokensForRegisteredCustomer() {
        UserAccount account = new UserAccount(9002L, PHONE, PHONE, "123456");
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        when(smsCodeStore.verifyAndConsume(PHONE, CODE, NOW)).thenReturn(true);
        when(userAccountRepository.findByPhone(PHONE)).thenReturn(Optional.of(account));
        when(loginUseCase.issueTokens(account, "user", "device-1"))
                .thenReturn(new TokenPair("jwt-access", "rt-1"));

        TokenPair result = useCase.login(PHONE, CODE, "device-1");

        assertEquals("jwt-access", result.accessToken());
        assertEquals("rt-1", result.refreshToken());
        verify(smsCodeStore).verifyAndConsume(PHONE, CODE, NOW);
    }

    @Test
    void unregisteredPhoneIsBlockedWithDedicatedCode() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        when(smsCodeStore.verifyAndConsume(PHONE, CODE, NOW)).thenReturn(true);
        when(userAccountRepository.findByPhone(PHONE)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class, () -> useCase.login(PHONE, CODE, "d"));

        assertEquals(ErrorCode.PHONE_NOT_REGISTERED, e.getErrorCode());
        assertTrue(e.resolvedMessage().contains("尚未注册"));
        verify(loginUseCase, never()).issueTokens(any(), anyString(), anyString());
    }

    @Test
    void wrongCodeRejectedBeforeLookup() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        when(smsCodeStore.verifyAndConsume(PHONE, "wrong", NOW)).thenReturn(false);

        BizException e = assertThrows(BizException.class, () -> useCase.login(PHONE, "wrong", "d"));

        assertEquals(ErrorCode.SMS_CODE_INVALID, e.getErrorCode());
        verify(userAccountRepository, never()).findByPhone(anyString());
    }

    @Test
    void blankDeviceIdFallsBackToDefault() {
        UserAccount account = new UserAccount(9002L, PHONE, PHONE, "123456");
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        when(smsCodeStore.verifyAndConsume(PHONE, CODE, NOW)).thenReturn(true);
        when(userAccountRepository.findByPhone(PHONE)).thenReturn(Optional.of(account));
        when(loginUseCase.issueTokens(account, "user", "default"))
                .thenReturn(new TokenPair("jwt-default", "rt-2"));

        assertEquals("jwt-default", useCase.login(PHONE, CODE, null).accessToken());
    }
}
