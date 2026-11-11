package com.sent.sports.auth.service.dto;

/**
 * 刷新令牌命令（中立入参）：刷新令牌。
 */
public record RefreshCmd(String refreshToken) {
}
