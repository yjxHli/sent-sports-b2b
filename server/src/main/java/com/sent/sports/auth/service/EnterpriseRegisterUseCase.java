package com.sent.sports.auth.service;

import com.sent.sports.auth.repository.SmsCodeStore;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.service.CustomerRegistrationService;
import com.sent.sports.customer.service.dto.RegisteredCustomer;
import com.sent.sports.finance.service.EnterpriseProfileRegistrationService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.regex.Pattern;

/**
 * 企业注册用例：先验证短信验证码（LOGIN scope，一次性），再一次事务落
 * customer 账号（ENTERPRISE）+ enterprise_profile（PENDING）。
 */
@Service
public class EnterpriseRegisterUseCase {

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    /** 统一社会信用代码：18 位，去掉易混淆的 I/O/S/V/Z。 */
    private static final Pattern CREDIT_CODE = Pattern.compile("^[0-9A-HJ-NPQRTUWXY]{18}$");

    private final CustomerRegistrationService registrationService;
    private final EnterpriseProfileRegistrationService profileRegistrationService;
    private final SmsCodeStore smsCodeStore;
    private final Clock clock;

    public EnterpriseRegisterUseCase(CustomerRegistrationService registrationService,
                                     EnterpriseProfileRegistrationService profileRegistrationService,
                                     SmsCodeStore smsCodeStore, Clock clock) {
        this.registrationService = registrationService;
        this.profileRegistrationService = profileRegistrationService;
        this.smsCodeStore = smsCodeStore;
        this.clock = clock;
    }

    /** 兼容既有内部调用；HTTP 注册必须走带验证码的重载。 */
    public RegisteredCustomer register(String phone, String password, String confirmPassword,
                                       String companyName, String creditCode, String licenseFileId) {
        return register(phone, password, confirmPassword, companyName, creditCode, licenseFileId, null);
    }

    @UseCase
    public RegisteredCustomer register(String phone, String password, String confirmPassword,
                                       String companyName, String creditCode, String licenseFileId,
                                       String smsCode) {
        if (phone == null || !PHONE.matcher(phone).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
        if (smsCode == null || smsCode.isBlank()
                || !smsCodeStore.verifyAndConsume(SmsCodeStore.SCOPE_LOGIN,
                        phone.trim(), smsCode.trim(), clock.millis())) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID, "短信验证码错误或已过期");
        }
        if (password == null || password.length() < 6 || password.length() > 20) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码长度需为 6~20 位");
        }
        if (!password.equals(confirmPassword)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "两次输入的密码不一致");
        }
        if (companyName == null || companyName.isBlank() || companyName.trim().length() > 128) {
            throw new BizException(ErrorCode.PARAM_INVALID, "企业名称不能为空且不能超过 128 字");
        }
        if (creditCode == null || !CREDIT_CODE.matcher(creditCode.trim().toUpperCase()).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "统一社会信用代码应为 18 位");
        }
        if (licenseFileId == null || licenseFileId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请上传营业执照");
        }

        String name = companyName.trim();
        String code = creditCode.trim().toUpperCase();
        RegisteredCustomer customer = registrationService.registerEnterprise(
                phone.trim(), password, "企业用户" + phone.substring(phone.length() - 4));
        profileRegistrationService.registerPending(customer.customerId(), name, code, licenseFileId.trim());
        return customer;
    }
}
