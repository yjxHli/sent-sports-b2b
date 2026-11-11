package com.sent.sports.web;

import com.sent.sports.auth.controller.AuthController;
import com.sent.sports.auth.service.LoginUseCase;
import com.sent.sports.auth.service.RefreshTokenUseCase;
import com.sent.sports.auth.service.dto.TokenPair;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
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
}
