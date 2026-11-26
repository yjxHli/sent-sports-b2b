package com.sent.sports.chat.domain;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

/**
 * 话术启用状态（V60）：{@code ENABLED} 参与机器人匹配，{@code DISABLED} 保留但不回复。
 *
 * <p>两种入口口径必须分开：</p>
 * <ul>
 *   <li>读库走 {@link #of(String)}：历史数据 / 脏值一律回落 {@code ENABLED}，
 *       否则一条脏状态就能让整条话术静默失效，客服还以为是机器人坏了；</li>
 *   <li>写入走 {@link #require(String)}：未知状态直接 {@code PARAM_INVALID}，
 *       让脏值从源头进不来。</li>
 * </ul>
 */
public enum FaqStatus {

    /** 启用：参与匹配。 */
    ENABLED,
    /** 停用：条目保留、命中次数保留，但不参与匹配。 */
    DISABLED;

    /** 读库口径：null / 空 / 未知值一律按启用处理。 */
    public static FaqStatus of(String raw) {
        return DISABLED.name().equalsIgnoreCase(trim(raw)) ? DISABLED : ENABLED;
    }

    /** 写入口径：只认白名单，别猜运营想表达什么。 */
    public static FaqStatus require(String raw) {
        String value = trim(raw);
        if (ENABLED.name().equalsIgnoreCase(value)) {
            return ENABLED;
        }
        if (DISABLED.name().equalsIgnoreCase(value)) {
            return DISABLED;
        }
        throw new BizException(ErrorCode.PARAM_INVALID, "状态只能是 ENABLED / DISABLED");
    }

    private static String trim(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
