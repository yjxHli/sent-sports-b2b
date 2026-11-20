package com.sent.sports.auth;

import com.sent.sports.auth.domain.UserAccount;
import com.sent.sports.auth.repository.SmsCodeStore;
import com.sent.sports.auth.repository.UserAccountRepository;
import com.sent.sports.auth.service.ResetPasswordUseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Clock;
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
 * 找回密码（TDD 红）：验证码一次性消费、账号必须是商城客户、两次密码校验先于消费验证码。
 */
class ResetPasswordUseCaseTest extends BaseUnitTest {

    private static final String PHONE = "13800138000";
    private static final String OLD = "old123456";

    @Mock
    private SmsCodeStore smsCodeStore;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private Clock clock;
    @InjectMocks
    private ResetPasswordUseCase useCase;

    @Test
    void rejectsShortOrMismatchedPasswordBeforeConsumingCode() {
        BizException shortPwd = assertThrows(BizException.class,
                () -> useCase.reset(PHONE, "123456", "abc", "abc"));
        assertEquals(ErrorCode.PARAM_INVALID, shortPwd.getErrorCode());

        BizException mismatch = assertThrows(BizException.class,
                () -> useCase.reset(PHONE, "123456", "new123456", "other123456"));
        assertEquals(ErrorCode.PARAM_INVALID, mismatch.getErrorCode());
        verify(smsCodeStore, never()).verifyAndConsume(any(), any(), any(), anyLong());
    }

    @Test
    void rejectsUnregisteredPhone() {
        when(userAccountRepository.findByPhone(PHONE)).thenReturn(Optional.empty());

        BizException e = assertThrows(BizException.class,
                () -> useCase.reset(PHONE, "123456", "new123456", "new123456"));

        assertEquals(ErrorCode.PHONE_NOT_REGISTERED, e.getErrorCode());
        verify(smsCodeStore, never()).verifyAndConsume(any(), any(), any(), anyLong());
    }

    @Test
    void rejectsWrongCodeWithoutSavingPassword() {
        when(userAccountRepository.findByPhone(PHONE))
                .thenReturn(Optional.of(new UserAccount(9002L, PHONE, PHONE, OLD)));
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        when(smsCodeStore.verifyAndConsume(SmsCodeStore.SCOPE_RESET, PHONE, "000000",
                FIXED_INSTANT.toEpochMilli())).thenReturn(false);

        BizException e = assertThrows(BizException.class,
                () -> useCase.reset(PHONE, "000000", "new123456", "new123456"));

        assertEquals(ErrorCode.SMS_CODE_INVALID, e.getErrorCode());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void resetsPasswordAndConsumesResetCode() {
        when(userAccountRepository.findByPhone(PHONE))
                .thenReturn(Optional.of(new UserAccount(9002L, PHONE, PHONE, OLD)));
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        when(smsCodeStore.verifyAndConsume(SmsCodeStore.SCOPE_RESET, PHONE, "654321",
                FIXED_INSTANT.toEpochMilli())).thenReturn(true);

        useCase.reset(PHONE, "654321", "new123456", "new123456");

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(captor.capture());
        assertEquals("new123456", captor.getValue().getPasswordHash());
        assertEquals(9002L, captor.getValue().getUserId());
        assertEquals(PHONE, captor.getValue().getPhone());
        verify(smsCodeStore).verifyAndConsume(eq(SmsCodeStore.SCOPE_RESET), eq(PHONE),
                eq("654321"), eq(FIXED_INSTANT.toEpochMilli()));
    }

    @Test
    void staffPhoneCannotResetThroughCustomerFlow() {
        UserAccount staff = new UserAccount(9001L, "admin", "hash");
        when(userAccountRepository.findByPhone(PHONE)).thenReturn(Optional.of(staff));

        BizException e = assertThrows(BizException.class,
                () -> useCase.reset(PHONE, "654321", "new123456", "new123456"));

        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verify(userAccountRepository, never()).save(any());
    }

    @Test
    void verifyCodeRejectsWrongCodeWithoutConsuming() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        when(smsCodeStore.verify(SmsCodeStore.SCOPE_RESET, PHONE, "000000",
                FIXED_INSTANT.toEpochMilli())).thenReturn(false);

        BizException e = assertThrows(BizException.class, () -> useCase.verifyCode(PHONE, "000000"));

        assertEquals(ErrorCode.SMS_CODE_INVALID, e.getErrorCode());
        verify(smsCodeStore, never()).verifyAndConsume(any(), any(), any(), anyLong());
    }

    @Test
    void verifyCodeAcceptsCorrectCodeWithoutConsuming() {
        when(clock.instant()).thenReturn(FIXED_INSTANT);
        when(smsCodeStore.verify(SmsCodeStore.SCOPE_RESET, PHONE, "654321",
                FIXED_INSTANT.toEpochMilli())).thenReturn(true);

        useCase.verifyCode(PHONE, "654321");

        verify(smsCodeStore).verify(SmsCodeStore.SCOPE_RESET, PHONE, "654321",
                FIXED_INSTANT.toEpochMilli());
        verify(smsCodeStore, never()).verifyAndConsume(any(), any(), any(), anyLong());
    }
}
