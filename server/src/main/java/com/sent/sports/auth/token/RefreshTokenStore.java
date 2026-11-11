package com.sent.sports.auth.token;

import java.util.Optional;

/**
 * 刷新令牌存储端口（P5-25）：一次性轮换 + 按设备隔离。
 * <p>口径：{@code rotate} 必须「校验旧令牌 → 失效旧令牌 → 写入新令牌」原子完成，
 * 旧令牌再用即失败（一次性），避免刷新令牌被重放；{@code revokeAll} 供改密/强制下线全端失效。
 * <p>本期默认进程内实现（Redis 未启动）；多实例部署换成 Redis 实现即可，用例层零改动。
 */
public interface RefreshTokenStore {

    /** 默认刷新令牌有效期：7 天（配置缺失时的兜底）。 */
    long DEFAULT_TTL_MILLIS = 7L * 24 * 3600 * 1000;

    /** 签发（覆盖同设备旧令牌）。 */
    void save(String aud, long userId, String deviceId, String token, long expireAtMillis);

    /**
     * 轮换：旧令牌有效且未过期时替换为新令牌并返回原会话信息；否则返回 empty。
     * 原子语义由实现保证（此处用 {@code ConcurrentHashMap.compute}）。
     */
    Optional<Session> rotate(String oldToken, String newToken, long newExpireAtMillis);

    /** 撤销某用户全部设备。 */
    void revokeAll(String aud, long userId);

    /** 撤销单个刷新令牌（登出当前设备）。 */
    void revoke(String token);

    /** 令牌当前的会话信息（未过期）。 */
    Optional<Session> find(String token);

    /** 会话快照：登录端、用户、设备、过期时间。 */
    record Session(String aud, long userId, String deviceId, long expireAtMillis) {
    }
}
