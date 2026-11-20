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
import com.sent.sports.support.BaseApiTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 演示验证码回显开关契约测试（TDD 红→绿）：
 * 仅当 app.sms.expose-demo-code=true 时，发送验证码接口才在响应体里带 demoCode，
 * 供前端在浏览器控制台打印；默认关闭，避免生产环境泄露验证码。
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "app.sms.expose-demo-code=true")
class AuthDemoCodeApiTest extends BaseApiTest {

    @MockBean private LoginUseCase loginUseCase;
    @MockBean private RefreshTokenUseCase refreshTokenUseCase;
    @MockBean private RegisterUseCase registerUseCase;
    @MockBean private EnterpriseRegisterUseCase enterpriseRegisterUseCase;
    @MockBean private EnterpriseAuditProgressUseCase auditProgressUseCase;
    @MockBean private SendSmsCodeUseCase sendSmsCodeUseCase;
    @MockBean private LoginByCodeUseCase loginByCodeUseCase;
    @MockBean private ResetPasswordUseCase resetPasswordUseCase;

    @Test
    @DisplayName("开关开启：发送验证码响应携带 demoCode")
    void sendCodeEchoesDemoCodeWhenEnabled() throws Exception {
        when(sendSmsCodeUseCase.send("13800138000")).thenReturn("482913");

        mvc.perform(post("/api/auth/sms-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("phone", "13800138000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.demoCode").value("482913"));
    }

    @Test
    @DisplayName("开关开启：找回密码验证码也回显 demoCode")
    void resetCodeEchoesDemoCodeWhenEnabled() throws Exception {
        when(sendSmsCodeUseCase.sendForReset("13800138000")).thenReturn("654321");

        mvc.perform(post("/api/auth/reset/sms-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("phone", "13800138000"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.demoCode").value("654321"));
    }
}
