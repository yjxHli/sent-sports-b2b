package com.sent.sports.auth.repository;

/**
 * 短信验证码进程内存储端口（演示环境，不接真实短信网关）。
 *
 * <p>验证码一次性：{@link #verifyAndConsume} 成功即作废；错误尝试不消耗。
 * 时间由调用方显式传入，便于单测精确控制过期边界。</p>
 *
 * <p>按用途隔离：登录验证码与找回密码验证码各自独立，互不覆盖、互不通用；
 * 无 scope 的重载默认走登录场景，保持既有调用与测试不变。</p>
 */
public interface SmsCodeStore {

    String SCOPE_LOGIN = "LOGIN";
    String SCOPE_RESET = "RESET";

    /** 保存登录验证码（覆盖同手机号的旧验证码），expireAtMillis 为过期时刻。 */
    default void save(String phone, String code, long expireAtMillis) {
        save(SCOPE_LOGIN, phone, code, expireAtMillis);
    }

    /** 保存指定用途的验证码。 */
    void save(String scope, String phone, String code, long expireAtMillis);

    /** 该手机号是否还有未过期的登录验证码（用于 60s 内防重发）。 */
    default boolean hasValid(String phone, long nowMillis) {
        return hasValid(SCOPE_LOGIN, phone, nowMillis);
    }

    /** 该手机号在指定用途下是否还有未过期验证码。 */
    boolean hasValid(String scope, String phone, long nowMillis);

    /** 校验并消费登录验证码：匹配且未过期返回 true 且作废；否则返回 false 且保留。 */
    default boolean verifyAndConsume(String phone, String code, long nowMillis) {
        return verifyAndConsume(SCOPE_LOGIN, phone, code, nowMillis);
    }

    /** 校验并消费指定用途的验证码。 */
    boolean verifyAndConsume(String scope, String phone, String code, long nowMillis);

    /**
     * 只校验不消费（供找回密码「验证手机号」步骤使用）。
     * 验证码仍保留，最终重置时再一次性消费；60s TTL 限制试探窗口。
     */
    boolean verify(String scope, String phone, String code, long nowMillis);
}
