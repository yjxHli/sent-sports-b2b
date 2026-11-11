package com.sent.sports.auth.token;

/**
 * 账号当前 token_version 查询端口（P5-26）。
 * AuthFilter 校验 Access Token 的 {@code ver} 是否与账号当前 token_version 一致，
 * 不一致说明令牌已被强制下线/改密作废。真实实现由 P5 仓储提供，测试可用桩。
 */
public interface TokenVersionLookup {

    long currentVersion(String aud, long userId);
}
