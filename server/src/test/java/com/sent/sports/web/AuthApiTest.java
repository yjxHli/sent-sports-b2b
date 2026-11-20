package com.sent.sports.web;

import com.sent.sports.auth.controller.AuthController;
import com.sent.sports.auth.service.EnterpriseAuditProgressUseCase;
import com.sent.sports.auth.service.EnterpriseRegisterUseCase;
import com.sent.sports.auth.service.LoginByCodeUseCase;
import com.sent.sports.auth.service.LoginUseCase;
import com.sent.sports.auth.service.RefreshTokenUseCase;
import com.sent.sports.auth.service.RegisterUseCase;
import com.sent.sports.auth.service.ResetPasswordUseCase;
import com.sent.sports.auth.service.SendSmsCodeUseCase;
import com.sent.sports.auth.service.dto.TokenPair;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.service.dto.RegisteredCustomer;
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证接口契约测试（P4-05，红→绿）。
 * 登录返回双令牌、refresh 轮换、无效刷新令牌 40103、登出清 token。
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthApiTest extends BaseApiTest {

    @MockBean
    private LoginUseCase loginUseCase;

    @MockBean
    private RefreshTokenUseCase refreshTokenUseCase;

    @MockBean
    private RegisterUseCase registerUseCase;

    @MockBean
    private EnterpriseRegisterUseCase enterpriseRegisterUseCase;

    @MockBean
    private EnterpriseAuditProgressUseCase auditProgressUseCase;

    @MockBean
    private SendSmsCodeUseCase sendSmsCodeUseCase;

    @MockBean
    private LoginByCodeUseCase loginByCodeUseCase;

    @MockBean
    private ResetPasswordUseCase resetPasswordUseCase;

    @Test
    @DisplayName("登录成功返回双令牌与有效期")
    void loginReturnsTokenPair() throws Exception {
        when(loginUseCase.login(any())).thenReturn(new TokenPair("at-1", "rt-1"));
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", "13800138000", "password", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("at-1"))
                .andExpect(jsonPath("$.data.refreshToken").value("rt-1"))
                .andExpect(jsonPath("$.data.expiresIn").value(7200));
    }

    @Test
    @DisplayName("登录参数缺失返回 40001")
    void loginMissingParam() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("未注册手机号登录返回专用错误码 42202")
    void unregisteredPhoneReturnsDedicatedCode() throws Exception {
        when(loginUseCase.login(any()))
                .thenThrow(new BizException(ErrorCode.PHONE_NOT_REGISTERED));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", "13900009999", "password", "123456", "aud", "user"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(42202))
                .andExpect(jsonPath("$.message").value("该手机号尚未注册，请先注册"));
    }

    @Test
    @DisplayName("发送验证码返回成功（验证码在后端控制台打印）")
    void sendSmsCodeReturnsOk() throws Exception {
        mvc.perform(post("/api/auth/sms-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("phone", "13800138000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(sendSmsCodeUseCase).send("13800138000");
    }

    @Test
    @DisplayName("验证码登录成功返回双令牌")
    void loginByCodeReturnsTokenPair() throws Exception {
        when(loginByCodeUseCase.login(any(), any(), any()))
                .thenReturn(new TokenPair("at-code", "rt-code"));

        mvc.perform(post("/api/auth/login/by-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("phone", "13800138000", "code", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("at-code"))
                .andExpect(jsonPath("$.data.refreshToken").value("rt-code"))
                .andExpect(jsonPath("$.data.expiresIn").value(7200));
    }

    @Test
    @DisplayName("refresh 无效令牌返回 40103")
    void refreshInvalidReturns40103() throws Exception {
        when(refreshTokenUseCase.refresh(any()))
                .thenThrow(new BizException(ErrorCode.REFRESH_TOKEN_INVALID));
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("refreshToken", "bad"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));
    }

    @Test
    @DisplayName("refresh 成功返回新双令牌")
    void refreshReturnsNewPair() throws Exception {
        when(refreshTokenUseCase.refresh(any())).thenReturn(new TokenPair("at-2", "rt-2"));
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("refreshToken", "rt-old"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").value("at-2"))
                .andExpect(jsonPath("$.data.refreshToken").value("rt-2"));
    }

    @Test
    @DisplayName("登出清 token 返回成功")
    void logoutReturnsOk() throws Exception {
        doNothing().when(refreshTokenUseCase).logout(any());
        mvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("refreshToken", "rt-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("企业注册返回 PENDING，不签发令牌")
    void enterpriseRegisterReturnsPending() throws Exception {
        when(enterpriseRegisterUseCase.register(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new RegisteredCustomer(9100L, "13912345678", "企业用户5678", "CUS-9100"));

        mvc.perform(post("/api/auth/enterprise/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "phone", "13912345678",
                                "password", "secret1",
                                "confirmPassword", "secret1",
                                "companyName", "森特测试有限公司",
                                "creditCode", "91310000MA1FL0AB1X",
                                "licenseFileId", "lic-file-1",
                                "code", "123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.auditStatus").value("PENDING"));
    }

    @Test
    @DisplayName("找回密码发送验证码走独立用例")
    void sendResetCodeUsesDedicatedUseCase() throws Exception {
        when(sendSmsCodeUseCase.sendForReset("13800138000")).thenReturn("654321");

        mvc.perform(post("/api/auth/reset/sms-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("phone", "13800138000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(sendSmsCodeUseCase).sendForReset("13800138000");
    }

    @Test
    @DisplayName("找回密码第一步校验验证码")
    void verifyResetCodeUsesDedicatedUseCase() throws Exception {
        mvc.perform(post("/api/auth/reset/verify-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("phone", "13800138000", "code", "654321"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(resetPasswordUseCase).verifyCode("13800138000", "654321");
    }

    @Test
    @DisplayName("确认重置调用重置密码用例")
    void resetPasswordUsesDedicatedUseCase() throws Exception {
        mvc.perform(post("/api/auth/reset/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "phone", "13800138000",
                                "code", "654321",
                                "password", "new123456",
                                "confirmPassword", "new123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(resetPasswordUseCase).reset("13800138000", "654321", "new123456", "new123456");
    }

    @Test
    @DisplayName("重置密码缺少验证码返回 40001")
    void resetPasswordMissingCode() throws Exception {
        mvc.perform(post("/api/auth/reset/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "phone", "13800138000",
                                "password", "new123456",
                                "confirmPassword", "new123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
