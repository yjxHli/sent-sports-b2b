package com.sent.sports.auth.service;

import com.sent.sports.auth.domain.UserAccount;
import com.sent.sports.auth.repository.SmsCodeStore;
import com.sent.sports.auth.repository.UserAccountRepository;
import com.sent.sports.auth.service.dto.TokenPair;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * 验证码登录（演示环境）：先验验证码，再解析客户账号，最后复用密码登录的双令牌签发链路。
 *
 * <p>未注册手机号同样抛 {@link ErrorCode#PHONE_NOT_REGISTERED}，与密码登录共用同一拦截口径；
 * 企业账号仍需审核通过，避免验证码登录绕过企业门禁。</p>
 */
@Service
public class LoginByCodeUseCase {

    private final SmsCodeStore smsCodeStore;
    private final UserAccountRepository userAccountRepository;
    private final LoginUseCase loginUseCase;
    private final Clock clock;

    public LoginByCodeUseCase(SmsCodeStore smsCodeStore, UserAccountRepository userAccountRepository,
                              LoginUseCase loginUseCase, Clock clock) {
        this.smsCodeStore = smsCodeStore;
        this.userAccountRepository = userAccountRepository;
        this.loginUseCase = loginUseCase;
        this.clock = clock;
    }

    @UseCase
    public TokenPair login(String phone, String code, String deviceId) {
        long now = clock.instant().toEpochMilli();
        if (phone == null || code == null || !smsCodeStore.verifyAndConsume(phone, code, now)) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }
        UserAccount account = userAccountRepository.findByPhone(phone)
                .orElseThrow(() -> new BizException(ErrorCode.PHONE_NOT_REGISTERED));
        // 验证码登录只面向商城客户，不得用于签发 admin 令牌
        if (!UserAccount.AUD_USER.equals(account.getAud())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        LoginUseCase.ensureEnterpriseApproved(account);
        String normalizedDeviceId = (deviceId == null || deviceId.isBlank()) ? "default" : deviceId;
        return loginUseCase.issueTokens(account, UserAccount.AUD_USER, normalizedDeviceId);
    }
}
