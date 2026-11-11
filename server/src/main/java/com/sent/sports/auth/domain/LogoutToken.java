package com.sent.sports.auth.domain;

/**
 * 登出黑名单令牌（auth 模块，P3-63）：记录已登出/已失效的刷新令牌，刷新时拦截。
 * 纯 Java 领域对象，无 Spring 依赖。
 */
public record LogoutToken(String token, long createdAtMillis) {
}
