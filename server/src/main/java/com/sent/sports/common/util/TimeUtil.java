package com.sent.sports.common.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 时间换算工具（common，无业务依赖）：在「毫秒时间戳」与「{@code DATETIME(3)} 对应的
 * {@link LocalDateTime}」之间互转。
 *
 * <p>库里时间列统一是 {@code DATETIME(3)}（无时区），对外接口统一传毫秒时间戳，
 * 换算必须只有一处实现，否则写入与读回会差出时区偏移（会员等级有效期曾因未落库，
 * 前端只能显示 1970-01-01）。默认按服务器本地时区换算。</p>
 */
public final class TimeUtil {

    /** 业务时区：与 {@code ClockConfig} 注入的 Clock 保持一致，不受部署机默认时区影响。 */
    public static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");

    private TimeUtil() {
    }

    /** 毫秒时间戳 → 本地日期时间；非正数视为「未设置」，返回 null。 */
    public static LocalDateTime fromMillis(Long millis) {
        if (millis == null || millis <= 0L) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), BIZ_ZONE);
    }

    /** 本地日期时间 → 毫秒时间戳；null 视为「未设置」，返回 0。 */
    public static long toMillis(LocalDateTime dateTime) {
        return toMillis(dateTime, BIZ_ZONE);
    }

    /** 本地日期时间 → 毫秒时间戳（指定时区，便于测试固定时区）。 */
    public static long toMillis(LocalDateTime dateTime, ZoneId zone) {
        if (dateTime == null) {
            return 0L;
        }
        return dateTime.atZone(zone == null ? BIZ_ZONE : zone)
                .toInstant()
                .toEpochMilli();
    }
}
