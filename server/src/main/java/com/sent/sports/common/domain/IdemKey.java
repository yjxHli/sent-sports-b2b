package com.sent.sports.common.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.regex.Pattern;

/**
 * 幂等键值对象（plan §7.12 / ADR-12）：来自请求头 {@code Idempotency-Key}。
 * 格式校验：非空、长度 ≤ 64、仅含 [A-Za-z0-9_-]（UUID 形态）。
 * 非法时抛 PARAM_INVALID(40001)；缺失由拦截器单独以 IDEMPOTENCY_KEY_MISSING(40002) 处理。
 */
public final class IdemKey {

    private static final int MAX_LEN = 64;
    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private final String value;

    private IdemKey(String value) {
        this.value = value;
    }

    public static boolean isValid(String raw) {
        if (raw == null) {
            return false;
        }
        String v = raw.trim();
        return PATTERN.matcher(v).matches();
    }

    public static IdemKey of(String raw) {
        if (raw == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        String v = raw.trim();
        if (v.isEmpty() || v.length() > MAX_LEN || !PATTERN.matcher(v).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        return new IdemKey(v);
    }

    public String value() {
        return value;
    }
}
