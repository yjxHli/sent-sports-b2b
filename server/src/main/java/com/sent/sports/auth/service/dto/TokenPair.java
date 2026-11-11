package com.sent.sports.auth.service.dto;

/**
 * 令牌对（中立出参）：访问令牌 + 刷新令牌。
 */
public record TokenPair(String accessToken, String refreshToken) {
}
