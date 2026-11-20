package com.sent.sports.auth.repository.impl;

import com.sent.sports.auth.repository.SmsCodeStore;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证码进程内存储（演示环境默认实现）。
 *
 * <p>键为「用途:手机号」，因此登录与找回密码的验证码互不覆盖、互不通用。
 * 读时惰性清理过期项，不引入后台线程。{@code verifyAndConsume} 用
 * {@link ConcurrentHashMap#computeIfPresent} 在键上原子完成「校验 + 用后即焚」，
 * 并发核验同一用途同一手机号时只有一个能成功。</p>
 */
@Component
public class InMemorySmsCodeStore implements SmsCodeStore {

    private final Map<String, Entry> byScopeAndPhone = new ConcurrentHashMap<>();

    @Override
    public void save(String scope, String phone, String code, long expireAtMillis) {
        byScopeAndPhone.put(key(scope, phone), new Entry(code, expireAtMillis));
    }

    @Override
    public boolean hasValid(String scope, String phone, long nowMillis) {
        if (phone == null) {
            return false;
        }
        String key = key(scope, phone);
        Entry entry = byScopeAndPhone.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.expireAtMillis() <= nowMillis) {
            byScopeAndPhone.remove(key, entry);
            return false;
        }
        return true;
    }

    @Override
    public boolean verifyAndConsume(String scope, String phone, String code, long nowMillis) {
        if (phone == null || code == null) {
            return false;
        }
        final boolean[] matched = {false};
        byScopeAndPhone.computeIfPresent(key(scope, phone), (key, entry) -> {
            if (entry.expireAtMillis() <= nowMillis) {
                return null; // 过期即清除
            }
            if (Objects.equals(entry.code(), code)) {
                matched[0] = true;
                return null; // 用后即焚
            }
            return entry; // 输错不消耗，允许重试
        });
        return matched[0];
    }

    private static String key(String scope, String phone) {
        return (scope == null ? SCOPE_LOGIN : scope) + ":" + phone;
    }

    @Override
    public boolean verify(String scope, String phone, String code, long nowMillis) {
        if (phone == null || code == null) {
            return false;
        }
        String key = key(scope, phone);
        Entry entry = byScopeAndPhone.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.expireAtMillis() <= nowMillis) {
            byScopeAndPhone.remove(key, entry);
            return false;
        }
        return Objects.equals(entry.code(), code);
    }

    private record Entry(String code, long expireAtMillis) {
    }
}
