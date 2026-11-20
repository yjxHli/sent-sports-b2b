package com.sent.sports.auth.service;

import com.sent.sports.auth.repository.SmsCodeStore;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.service.EnterpriseProfileQueryService;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * 企业审核进度查询：未审核通过的企业拿不到令牌，
 * 所以用「手机号 + 一次性短信验证码」做匿名查询，避免泄露别家企业资料。
 */
@Service
public class EnterpriseAuditProgressUseCase {

    private final SmsCodeStore smsCodeStore;
    private final EnterpriseProfileQueryService profileQueryService;
    private final Clock clock;

    public EnterpriseAuditProgressUseCase(SmsCodeStore smsCodeStore,
                                          EnterpriseProfileQueryService profileQueryService,
                                          Clock clock) {
        this.smsCodeStore = smsCodeStore;
        this.profileQueryService = profileQueryService;
        this.clock = clock;
    }

    @UseCase
    public EnterpriseProfileQueryService.AuditProgress get(String phone, String code) {
        if (phone == null || phone.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入手机号");
        }
        String normalizedPhone = phone.trim();
        if (code == null || code.isBlank()
                || !smsCodeStore.verifyAndConsume(SmsCodeStore.SCOPE_LOGIN,
                        normalizedPhone, code.trim(), clock.millis())) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID, "短信验证码错误或已过期");
        }
        EnterpriseProfileQueryService.AuditProgress progress = profileQueryService.findByPhone(normalizedPhone);
        if (progress == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "未找到该手机号对应的企业认证资料");
        }
        return progress;
    }
}
