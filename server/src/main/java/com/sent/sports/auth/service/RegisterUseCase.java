package com.sent.sports.auth.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.coupon.service.RegisterCouponGrant;
import com.sent.sports.customer.service.CustomerRegistrationService;
import com.sent.sports.customer.service.dto.RegisteredCustomer;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/** 注册用例：校验手机号 / 两次密码，随后直连数据库建 customer 账号（不写任何内存仓储）。 */
@Service
public class RegisterUseCase {

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 20;

    private final CustomerRegistrationService registrationService;
    private final RegisterCouponGrant registerCouponGrant;

    public RegisterUseCase(CustomerRegistrationService registrationService,
                           RegisterCouponGrant registerCouponGrant) {
        this.registrationService = registrationService;
        this.registerCouponGrant = registerCouponGrant;
    }

    @UseCase
    public RegisteredCustomer register(String phone, String password, String confirmPassword) {
        if (phone == null || !PHONE.matcher(phone).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "手机号格式不正确");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH
                || password.length() > MAX_PASSWORD_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码长度需为 6~20 位");
        }
        if (!password.equals(confirmPassword)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "两次输入的密码不一致");
        }
        var created = registrationService.register(phone, password, defaultNickname(phone));
        // 注册礼与建账号同处 @UseCase 事务：发券/站内信失败则注册一起回滚
        registerCouponGrant.grant(created.customerId());
        return created;
    }

    /** 默认昵称：用户名取「用户 + 手机号尾号四位」，头像由前端按首字兜底。 */
    public static String defaultNickname(String phone) {
        return "用户" + phone.substring(phone.length() - 4);
    }
}
