package com.sent.sports.auth;

import com.sent.sports.auth.service.RegisterUseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.coupon.service.RegisterCouponGrant;
import com.sent.sports.customer.service.CustomerRegistrationService;
import com.sent.sports.customer.service.dto.RegisteredCustomer;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 注册用例：手机号 / 密码格式、两次一致、默认昵称（手机号尾号四位）。 */
class RegisterUseCaseTest extends BaseUseCaseTest {

    @Mock
    private CustomerRegistrationService registrationService;
    @Mock
    private RegisterCouponGrant registerCouponGrant;
    @InjectMocks
    private RegisterUseCase useCase;

    @Test
    void registersWithDefaultNicknameFromPhoneTail() {
        when(registrationService.register(eq("13800007777"), anyString(), anyString()))
                .thenReturn(new RegisteredCustomer(42L, "13800007777", "用户7777", "CUS-42"));

        var result = useCase.register("13800007777", "secret123", "secret123");

        assertEquals(42L, result.customerId());
        assertEquals("用户7777", result.nickname());
        ArgumentCaptor<String> nickname = ArgumentCaptor.forClass(String.class);
        verify(registrationService).register(eq("13800007777"), eq("secret123"), nickname.capture());
        assertEquals("用户7777", nickname.getValue());
        verify(registerCouponGrant).grant(42L);
    }

    @Test
    void rejectsInvalidPhone() {
        BizException e = assertThrows(BizException.class,
                () -> useCase.register("12345", "secret123", "secret123"));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(registrationService, never()).register(anyString(), anyString(), anyString());
    }

    @Test
    void rejectsShortPassword() {
        BizException e = assertThrows(BizException.class,
                () -> useCase.register("13800007777", "123", "123"));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(registrationService, never()).register(anyString(), anyString(), anyString());
    }

    @Test
    void rejectsMismatchedConfirmation() {
        BizException e = assertThrows(BizException.class,
                () -> useCase.register("13800007777", "secret123", "secret456"));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(registrationService, never()).register(anyString(), anyString(), anyString());
    }
}
