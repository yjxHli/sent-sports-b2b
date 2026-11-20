package com.sent.sports.auth.service;

import com.sent.sports.auth.domain.UserAccount;
import com.sent.sports.auth.repository.SmsCodeStore;
import com.sent.sports.auth.repository.UserAccountRepository;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.regex.Pattern;

/**
 * 找回密码：校验找回密码专用验证码（用后即焚）→ 确认商城客户 → 落库新密码。
 *
 * <p>验证码与登录验证码按 scope 隔离，互不覆盖、互不通用；输入校验放在消费验证码之前，
 * 避免密码填错就把有效验证码白白作废。</p>
 */
@Service
public class ResetPasswordUseCase {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 20;

    private final SmsCodeStore smsCodeStore;
    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    public ResetPasswordUseCase(SmsCodeStore smsCodeStore, UserAccountRepository userAccountRepository,
                                Clock clock) {
        this.smsCodeStore = smsCodeStore;
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    /**
     * 「验证手机号」步骤：只校验找回密码验证码，不消费；最终重置时再一次性消费。
     */
    public void verifyCode(String phone, String code) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
        if (code == null || !code.matches("\\d{6}")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入 6 位验证码");
        }
        long now = clock.instant().toEpochMilli();
        if (!smsCodeStore.verify(SmsCodeStore.SCOPE_RESET, phone, code, now)) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }
    }

    @UseCase
    public void reset(String phone, String code, String password, String confirmPassword) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码长度需为 6~20 位");
        }
        if (!password.equals(confirmPassword)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "两次输入的密码不一致");
        }

        UserAccount account = userAccountRepository.findByPhone(phone)
                .orElseThrow(() -> new BizException(ErrorCode.PHONE_NOT_REGISTERED));
        if (!UserAccount.AUD_USER.equals(account.getAud())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        long now = clock.instant().toEpochMilli();
        if (code == null || !smsCodeStore.verifyAndConsume(SmsCodeStore.SCOPE_RESET, phone, code, now)) {
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }
        userAccountRepository.save(account.withPassword(password));
    }
}
