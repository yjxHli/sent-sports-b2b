package com.sent.sports.auth.service;

import com.sent.sports.auth.repository.SmsCodeStore;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.regex.Pattern;

/**
 * 发送登录验证码（演示环境）：不调用真实短信网关，验证码存进程内并打印到后端控制台。
 *
 * <p>有效期与前端 60s 倒计时一致；60s 内重复发送用 {@link ErrorCode#TOO_FREQUENT} 拒绝，
 * 服务端与前端双重防连点。</p>
 */
@Service
public class SendSmsCodeUseCase {

    private static final Logger log = LoggerFactory.getLogger(SendSmsCodeUseCase.class);
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final long TTL_MILLIS = 60_000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SmsCodeStore smsCodeStore;
    private final Clock clock;

    public SendSmsCodeUseCase(SmsCodeStore smsCodeStore, Clock clock) {
        this.smsCodeStore = smsCodeStore;
        this.clock = clock;
    }

    @UseCase
    public String send(String phone) {
        return send(SmsCodeStore.SCOPE_LOGIN, phone, "短信验证码");
    }

    /** 找回密码验证码：与登录码同机制（生成/存储/60s/限流），但独立 scope 互不干扰。 */
    @UseCase
    public String sendForReset(String phone) {
        return send(SmsCodeStore.SCOPE_RESET, phone, "找回密码验证码");
    }

    private String send(String scope, String phone, String purpose) {
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
        long now = clock.instant().toEpochMilli();
        if (smsCodeStore.hasValid(scope, phone, now)) {
            throw new BizException(ErrorCode.TOO_FREQUENT, "验证码发送过于频繁，请 60 秒后重试");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        smsCodeStore.save(scope, phone, code, now + TTL_MILLIS);
        log.info("【演示环境】{}：手机号 {}，验证码 {}，60 秒内有效（未调用真实短信网关）",
                purpose, mask(phone), code);
        return code;
    }

    private static String mask(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
