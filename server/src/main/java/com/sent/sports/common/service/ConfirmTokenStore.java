package com.sent.sports.common.service;

/**
 * 确认令牌存储端口（P5-31）：把「一次性、带 TTL、绑定动作与目标」的存储能力抽出来，
 * 服务层只管校验短语与生成令牌，换 Redis 只需替换实现，业务代码零改动。
 * <p>刻意不追求分布式强一致：确认令牌本就是短时有效、单次使用的凭证，TTL + 用后即焚已足够；
 * 本期默认实现为进程内存储（{@code InMemoryConfirmTokenStore}），多实例部署时替换为 Redis 实现即可。
 */
public interface ConfirmTokenStore {

    /** 读取未过期的令牌；不存在或已过期返回 {@code null}。 */
    Stored find(String key);

    /** 写入令牌（覆盖同键旧值）。 */
    void save(String key, String token, long expireAtMillis);

    /**
     * 用后即焚：键存在、未过期且令牌匹配时删除并返回 {@code true}；
     * 否则不改动存储并返回 {@code false}（防止用错令牌把别的动作的令牌消耗掉）。
     */
    boolean consume(String key, String token);

    /** 存储中的令牌快照。 */
    record Stored(String token, long expireAtMillis) {
    }
}
