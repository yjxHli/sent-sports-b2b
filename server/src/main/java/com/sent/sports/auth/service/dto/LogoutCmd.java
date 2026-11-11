package com.sent.sports.auth.service.dto;

/**
 * 登出命令（中立入参）：待拉黑的刷新令牌。
 */
public record LogoutCmd(String refreshToken) {
}
