package com.sent.sports.auth;

import com.sent.sports.auth.repository.SmsCodeStore;
import com.sent.sports.auth.service.EnterpriseRegisterUseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.service.CustomerRegistrationService;
import com.sent.sports.customer.service.dto.RegisteredCustomer;
import com.sent.sports.finance.service.EnterpriseProfileRegistrationService;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 企业注册用例（TDD 红 → 绿）：客户账号 + 企业档案 PENDING 一起落库。
 * <p>2026-11-19 新增：注册必须验证短信验证码（LOGIN scope，一次性），
 * 否则任何人拿到营业执照 fileId 就能批量注册企业账号。</p>
 */
class EnterpriseRegisterUseCaseTest extends BaseUseCaseTest {

    private static final String PHONE = "13912345678";
    private static final String CREDIT_CODE = "91310000MA1FL0AB1X";
    private static final String CODE = "123456";
    private static final long NOW = 1_793_000_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);

    @Mock
    private CustomerRegistrationService registrationService;

    @Mock
    private EnterpriseProfileRegistrationService profileRegistrationService;

    @Mock
    private SmsCodeStore smsCodeStore;

    private EnterpriseRegisterUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new EnterpriseRegisterUseCase(
                registrationService, profileRegistrationService, smsCodeStore, CLOCK);
    }

    @Test
    void registersCustomerAndPendingEnterpriseProfile_whenSmsCodeValid() {
        RegisteredCustomer customer = new RegisteredCustomer(9100L, PHONE, "企业用户5678", "CUS-9100");
        when(smsCodeStore.verifyAndConsume(SmsCodeStore.SCOPE_LOGIN, PHONE, CODE, NOW)).thenReturn(true);
        when(registrationService.registerEnterprise(PHONE, "secret1", "企业用户5678"))
                .thenReturn(customer);

        RegisteredCustomer result = useCase.register(PHONE, "secret1", "secret1",
                "森特测试有限公司", CREDIT_CODE, "lic-file-1", CODE);

        assertThat(result).isEqualTo(customer);
        verify(profileRegistrationService).registerPending(9100L, "森特测试有限公司", CREDIT_CODE, "lic-file-1");
    }

    @Test
    void wrongSmsCodeRejectedBeforeWritingAnything() {
        when(smsCodeStore.verifyAndConsume(SmsCodeStore.SCOPE_LOGIN, PHONE, "000000", NOW)).thenReturn(false);

        assertThatThrownBy(() -> useCase.register(PHONE, "secret1", "secret1",
                "森特测试有限公司", CREDIT_CODE, "lic-file-1", "000000"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SMS_CODE_INVALID);
        verify(registrationService, never()).registerEnterprise(anyString(), anyString(), anyString());
        verify(profileRegistrationService, never())
                .registerPending(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void blankSmsCodeRejectedBeforeAnyValidationOrWrite() {
        assertThatThrownBy(() -> useCase.register(PHONE, "secret1", "secret1",
                "森特测试有限公司", CREDIT_CODE, "lic-file-1", "  "))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SMS_CODE_INVALID);
        verify(smsCodeStore, never()).verifyAndConsume(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void smsCodeIsConsumedBeforeCreditCodeValidation() {
        when(smsCodeStore.verifyAndConsume(SmsCodeStore.SCOPE_LOGIN, PHONE, CODE, NOW)).thenReturn(false);

        assertThatThrownBy(() -> useCase.register(PHONE, "secret1", "secret1",
                "森特测试有限公司", "BAD", "lic-file-1", CODE))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SMS_CODE_INVALID);
        verify(smsCodeStore).verifyAndConsume(SmsCodeStore.SCOPE_LOGIN, PHONE, CODE, NOW);
    }

    @Test
    void invalidCreditCodeRejectedWithoutRegistrationWhenCodeValid() {
        when(smsCodeStore.verifyAndConsume(SmsCodeStore.SCOPE_LOGIN, PHONE, CODE, NOW)).thenReturn(true);

        assertThatThrownBy(() -> useCase.register(PHONE, "secret1", "secret1",
                "森特测试有限公司", "BAD", "lic-file-1", CODE))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PARAM_INVALID);
        verify(registrationService, never()).registerEnterprise(anyString(), anyString(), anyString());
    }

    @Test
    void passwordMismatchRejectedBeforeWritingAnything() {
        when(smsCodeStore.verifyAndConsume(SmsCodeStore.SCOPE_LOGIN, PHONE, CODE, NOW)).thenReturn(true);

        assertThatThrownBy(() -> useCase.register(PHONE, "secret1", "secret2",
                "森特测试有限公司", CREDIT_CODE, "lic-file-1", CODE))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PARAM_INVALID);
        verify(registrationService, never()).registerEnterprise(anyString(), anyString(), anyString());
    }

    @Test
    void duplicatePhoneDoesNotCreateEnterpriseProfile() {
        when(smsCodeStore.verifyAndConsume(SmsCodeStore.SCOPE_LOGIN, PHONE, CODE, NOW)).thenReturn(true);
        when(registrationService.registerEnterprise(PHONE, "secret1", "企业用户5678"))
                .thenThrow(new BizException(ErrorCode.BIZ_REJECT, "该手机号已注册"));

        assertThatThrownBy(() -> useCase.register(PHONE, "secret1", "secret1",
                "森特测试有限公司", CREDIT_CODE, "lic-file-1", CODE))
                .isInstanceOf(BizException.class);
        verify(profileRegistrationService, never())
                .registerPending(anyLong(), anyString(), anyString(), anyString());
    }
}
