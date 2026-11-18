package com.sent.sports.auth.controller;

import com.sent.sports.auth.dto.AuthDtos;
import com.sent.sports.auth.service.EnterpriseRegisterUseCase;
import com.sent.sports.auth.service.LoginUseCase;
import com.sent.sports.auth.service.RefreshTokenUseCase;
import com.sent.sports.auth.service.RegisterUseCase;
import com.sent.sports.auth.service.dto.LoginCmd;
import com.sent.sports.auth.service.dto.LogoutCmd;
import com.sent.sports.auth.service.dto.RefreshCmd;
import com.sent.sports.auth.service.dto.TokenPair;
import com.sent.sports.common.web.R;
import jakarta.validation.Valid;
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

    private static final long ACCESS_TOKEN_TTL_SECONDS = 7200L;

    public AuthController(LoginUseCase loginUseCase, RefreshTokenUseCase refreshTokenUseCase,
                          RegisterUseCase registerUseCase,
                          EnterpriseRegisterUseCase enterpriseRegisterUseCase) {
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.registerUseCase = registerUseCase;
        this.enterpriseRegisterUseCase = enterpriseRegisterUseCase;
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
                cmd.confirmPassword(), cmd.companyName(), cmd.creditCode(), cmd.licenseFileId());
        return R.ok(new AuthDtos.EnterpriseRegisterVO(registered.phone(), registered.nickname(), "PENDING"));
    }

    @PostMapping("/login")
    public R<AuthDtos.TokenVO> login(@Valid @RequestBody AuthDtos.LoginCmd cmd) {
        TokenPair pair = loginUseCase.login(new LoginCmd(cmd.username(), cmd.password(),
                cmd.aud(), cmd.deviceId()));
        return R.ok(toVo(pair));
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
