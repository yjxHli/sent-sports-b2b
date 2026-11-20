package com.sent.sports.auth.controller;

import com.sent.sports.auth.dto.AuthDtos;
import com.sent.sports.auth.service.EnterpriseAuditProgressUseCase;
import com.sent.sports.auth.service.EnterpriseRegisterUseCase;
import com.sent.sports.auth.service.LoginByCodeUseCase;
import com.sent.sports.auth.service.LoginUseCase;
import com.sent.sports.auth.service.RefreshTokenUseCase;
import com.sent.sports.auth.service.RegisterUseCase;
import com.sent.sports.auth.service.ResetPasswordUseCase;
import com.sent.sports.auth.service.SendSmsCodeUseCase;
import com.sent.sports.auth.service.dto.LoginCmd;
import com.sent.sports.auth.service.dto.LogoutCmd;
import com.sent.sports.auth.service.dto.RefreshCmd;
import com.sent.sports.auth.service.dto.TokenPair;
import com.sent.sports.common.web.R;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器（P4-07）：登录 / 刷新 / 登出。
 * <p>登录与刷新为公开端点（plan §8.3），不标 {@code @RequirePerm}；
 * 依赖同模块用例 {@link LoginUseCase} / {@link RefreshTokenUseCase}（符合 ArchUnit 规则 1/4）。
 * 双令牌有效期：访问令牌 2 小时（7200 秒）。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;

    private final RefreshTokenUseCase refreshTokenUseCase;

    private final RegisterUseCase registerUseCase;

    private final EnterpriseRegisterUseCase enterpriseRegisterUseCase;

    private final EnterpriseAuditProgressUseCase auditProgressUseCase;

    private final SendSmsCodeUseCase sendSmsCodeUseCase;

    private final LoginByCodeUseCase loginByCodeUseCase;

    private final ResetPasswordUseCase resetPasswordUseCase;

    /** 演示开关：开启时把验证码回显到响应体，供前端打印控制台；生产默认关闭。 */
    private final boolean exposeDemoCode;

    private static final long ACCESS_TOKEN_TTL_SECONDS = 7200L;

    public AuthController(LoginUseCase loginUseCase, RefreshTokenUseCase refreshTokenUseCase,
                          RegisterUseCase registerUseCase,
                          EnterpriseRegisterUseCase enterpriseRegisterUseCase,
                          EnterpriseAuditProgressUseCase auditProgressUseCase,
                          SendSmsCodeUseCase sendSmsCodeUseCase,
                          LoginByCodeUseCase loginByCodeUseCase,
                          ResetPasswordUseCase resetPasswordUseCase,
                          @Value("${app.sms.expose-demo-code:false}") boolean exposeDemoCode) {
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.registerUseCase = registerUseCase;
        this.enterpriseRegisterUseCase = enterpriseRegisterUseCase;
        this.auditProgressUseCase = auditProgressUseCase;
        this.sendSmsCodeUseCase = sendSmsCodeUseCase;
        this.loginByCodeUseCase = loginByCodeUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
        this.exposeDemoCode = exposeDemoCode;
    }

    /** 商城注册：直连 customer 表落账号，前端拿到结果后自动走标准登录换取令牌。 */
    @PostMapping("/register")
    public R<AuthDtos.RegisterVO> register(@Valid @RequestBody AuthDtos.RegisterCmd cmd) {
        var registered = registerUseCase.register(cmd.phone(), cmd.password(), cmd.confirmPassword());
        return R.ok(new AuthDtos.RegisterVO(registered.customerId(), registered.phone(),
                registered.nickname(), registered.customerNo()));
    }

    /**
     * 企业注册：账号与企业档案一起落库（PENDING），不自动登录；管理端审核通过后才可登录。
     */
    @PostMapping("/enterprise/register")
    public R<AuthDtos.EnterpriseRegisterVO> enterpriseRegister(
            @Valid @RequestBody AuthDtos.EnterpriseRegisterCmd cmd) {
        var registered = enterpriseRegisterUseCase.register(cmd.phone(), cmd.password(),
                cmd.confirmPassword(), cmd.companyName(), cmd.creditCode(), cmd.licenseFileId(), cmd.code());
        return R.ok(new AuthDtos.EnterpriseRegisterVO(registered.phone(), registered.nickname(), "PENDING"));
    }

    /** 企业审核进度：未通过审核的企业无法登录，凭手机号 + 短信验证码匿名查询。 */
    @PostMapping("/enterprise/audit-progress")
    public R<com.sent.sports.finance.service.EnterpriseProfileQueryService.AuditProgress> auditProgress(
            @Valid @RequestBody AuthDtos.AuditProgressCmd cmd) {
        return R.ok(auditProgressUseCase.get(cmd.phone(), cmd.code()));
    }

    @PostMapping("/login")
    public R<AuthDtos.TokenVO> login(@Valid @RequestBody AuthDtos.LoginCmd cmd) {
        TokenPair pair = loginUseCase.login(new LoginCmd(cmd.username(), cmd.password(),
                cmd.aud(), cmd.deviceId()));
        return R.ok(toVo(pair));
    }

    /** 演示环境：发送验证码——存进程内、打印后端控制台；开关开启时回显给前端打印浏览器控制台。 */
    @PostMapping("/sms-code")
    public R<AuthDtos.SendCodeVO> sendSmsCode(@Valid @RequestBody AuthDtos.SendCodeCmd cmd) {
        String code = sendSmsCodeUseCase.send(cmd.phone());
        return R.ok(new AuthDtos.SendCodeVO(exposeDemoCode ? code : null));
    }

    /** 验证码登录：与密码登录共用双令牌签发链路，未注册手机号返回同一拦截码。 */
    @PostMapping("/login/by-code")
    public R<AuthDtos.TokenVO> loginByCode(@Valid @RequestBody AuthDtos.LoginByCodeCmd cmd) {
        return R.ok(toVo(loginByCodeUseCase.login(cmd.phone(), cmd.code(), cmd.deviceId())));
    }

    /** 找回密码发送验证码：与登录验证码同机制，但 scope 独立、互不覆盖。 */
    @PostMapping("/reset/sms-code")
    public R<AuthDtos.SendCodeVO> sendResetCode(@Valid @RequestBody AuthDtos.SendResetCodeCmd cmd) {
        String code = sendSmsCodeUseCase.sendForReset(cmd.phone());
        return R.ok(new AuthDtos.SendCodeVO(exposeDemoCode ? code : null));
    }

    /** 找回密码第一步：校验验证码但保留，通过后才允许设置新密码。 */
    @PostMapping("/reset/verify-code")
    public R<Void> verifyResetCode(@Valid @RequestBody AuthDtos.VerifyResetCodeCmd cmd) {
        resetPasswordUseCase.verifyCode(cmd.phone(), cmd.code());
        return R.ok();
    }

    /** 找回密码确认重置：校验用后即焚的验证码后把新密码落库。 */
    @PostMapping("/reset/password")
    public R<Void> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordCmd cmd) {
        resetPasswordUseCase.reset(cmd.phone(), cmd.code(), cmd.password(), cmd.confirmPassword());
        return R.ok();
    }

    @PostMapping("/refresh")
    public R<AuthDtos.TokenVO> refresh(@Valid @RequestBody AuthDtos.RefreshCmd cmd) {
        TokenPair pair = refreshTokenUseCase.refresh(new RefreshCmd(cmd.refreshToken()));
        return R.ok(toVo(pair));
    }

    @PostMapping("/logout")
    public R<Void> logout(@Valid @RequestBody AuthDtos.RefreshCmd cmd) {
        refreshTokenUseCase.logout(new LogoutCmd(cmd.refreshToken()));
        return R.ok();
    }

    private AuthDtos.TokenVO toVo(TokenPair pair) {
        return new AuthDtos.TokenVO(pair.accessToken(), pair.refreshToken(), ACCESS_TOKEN_TTL_SECONDS);
    }
}
