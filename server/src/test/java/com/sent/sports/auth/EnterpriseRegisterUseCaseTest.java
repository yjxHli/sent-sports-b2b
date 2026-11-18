package com.sent.sports.auth;

import com.sent.sports.auth.service.EnterpriseRegisterUseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.service.CustomerRegistrationService;
import com.sent.sports.customer.service.dto.RegisteredCustomer;
import com.sent.sports.finance.service.EnterpriseProfileRegistrationService;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 企业注册用例（TDD 红）：客户账号 + 企业档案 PENDING 一起落库，
 * 用户不可见客户号；验证码暂不实现，但营业执照/信用代码必须真校验。
 */
class EnterpriseRegisterUseCaseTest extends BaseUseCaseTest {

    private static final String PHONE = "13912345678";
    private static final String CREDIT_CODE = "91310000MA1FL0AB1X";

    @Mock
    private CustomerRegistrationService registrationService;

    @Mock
    private EnterpriseProfileRegistrationService profileRegistrationService;

    @InjectMocks
    private EnterpriseRegisterUseCase useCase;

    @Test
    void registersCustomerAndPendingEnterpriseProfile() {
        RegisteredCustomer customer = new RegisteredCustomer(9100L, PHONE, "企业用户5678", "CUS-9100");
        when(registrationService.registerEnterprise(PHONE, "secret1", "企业用户5678"))
                .thenReturn(customer);

        RegisteredCustomer result = useCase.register(PHONE, "secret1", "secret1",
                "森特测试有限公司", CREDIT_CODE, "lic-file-1");

        assertThat(result).isEqualTo(customer);
        verify(profileRegistrationService).registerPending(9100L, "森特测试有限公司", CREDIT_CODE, "lic-file-1");
    }

    @Test
    void invalidCreditCodeRejectedBeforeWritingAnything() {
        assertThatThrownBy(() -> useCase.register(PHONE, "secret1", "secret1",
                "森特测试有限公司", "BAD", "lic-file-1"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PARAM_INVALID);
        verify(registrationService, never()).registerEnterprise(anyString(), anyString(), anyString());
    }

    @Test
    void passwordMismatchRejectedBeforeWritingAnything() {
        assertThatThrownBy(() -> useCase.register(PHONE, "secret1", "secret2",
                "森特测试有限公司", CREDIT_CODE, "lic-file-1"))
                .isInstanceOf(BizException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PARAM_INVALID);
        verify(registrationService, never()).registerEnterprise(anyString(), anyString(), anyString());
    }

    @Test
    void duplicatePhoneDoesNotCreateEnterpriseProfile() {
        when(registrationService.registerEnterprise(PHONE, "secret1", "企业用户5678"))
                .thenThrow(new BizException(ErrorCode.BIZ_REJECT, "该手机号已注册"));

        assertThatThrownBy(() -> useCase.register(PHONE, "secret1", "secret1",
                "森特测试有限公司", CREDIT_CODE, "lic-file-1"))
                .isInstanceOf(BizException.class);
        verify(profileRegistrationService, never())
                .registerPending(anyLong(), anyString(), anyString(), anyString());
    }
}
