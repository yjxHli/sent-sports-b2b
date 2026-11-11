package com.sent.sports.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 认证接口 Web 层 DTO（P4-06）。
 * 请求体承载外部入参并带校验注解；响应体 {@link TokenVO} 暴露给前端。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 登录请求：用户名 + 密码 + 可选登录端/设备标识（P5-25 按设备隔离刷新令牌）。 */
    public record LoginCmd(
            @NotBlank(message = "用户名不能为空") String username,
            @NotBlank(message = "密码不能为空") String password,
            String aud,
            String deviceId) {
    }

    /** 刷新 / 登出请求：携带刷新令牌。 */
    public record RefreshCmd(
            @NotBlank(message = "刷新令牌不能为空") String refreshToken) {
    }

    /** 令牌响应：双令牌 + 访问令牌有效期（秒）。 */
    public record TokenVO(String accessToken, String refreshToken, long expiresIn) {
    }
}
