package com.sent.sports.auth.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.service.CustomerRegistrationService;
import com.sent.sports.customer.service.dto.RegisteredCustomer;
import com.sent.sports.finance.service.EnterpriseProfileRegistrationService;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 企业注册用例：customer 账号（ENTERPRISE）+ enterprise_profile（PENDING）一次事务落库。
 * <p>验证码暂不实现；营业执照 fileId、统一社会信用代码必填，审核统一由管理端完成。</p>
 */
@Service
public class EnterpriseRegisterUseCase {

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    /** 统一社会信用代码：18 位，去掉易混淆的 I/O/S/V/Z。 */
    private static final Pattern CREDIT_CODE = Pattern.compile("^[0-9A-HJ-NPQRTUWXY]{18}$");

    private final CustomerRegistrationService registrationService;
    private final EnterpriseProfileRegistrationService profileRegistrationService;

    public EnterpriseRegisterUseCase(CustomerRegistrationService registrationService,
                                     EnterpriseProfileRegistrationService profileRegistrationService) {
        this.registrationService = registrationService;
        this.profileRegistrationService = profileRegistrationService;
    }

    @UseCase
    public RegisteredCustomer register(String phone, String password, String confirmPassword,
                                       String companyName, String creditCode, String licenseFileId) {
        if (phone == null || !PHONE.matcher(phone).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
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
                phone, password, "企业用户" + phone.substring(phone.length() - 4));
        profileRegistrationService.registerPending(customer.customerId(), name, code, licenseFileId.trim());
        return customer;
    }
}
