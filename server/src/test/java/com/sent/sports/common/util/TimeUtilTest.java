package com.sent.sports.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 时间换算（TDD 红→绿）：库里 {@code DATETIME(3)} 与接口毫秒时间戳之间必须能原样往返，
 * 否则授级有效期写进去再读出来会差出时区偏移。
 */
class TimeUtilTest {

    @Test
    @DisplayName("毫秒 ↔ DATETIME 往返不丢精度（毫秒级）")
    void roundTripKeepsMillis() {
        long millis = 1_893_456_000_000L;

        LocalDateTime dateTime = TimeUtil.fromMillis(millis);

        assertEquals(millis, TimeUtil.toMillis(dateTime));
        assertEquals(TimeUtil.BIZ_ZONE, dateTime.atZone(TimeUtil.BIZ_ZONE).getZone());
    }

    @Test
    @DisplayName("非正数与 null 视为「未设置」：写库落 NULL，读回算 0（不产生 1970-01-01）")
    void nonPositiveIsUnset() {
        assertNull(TimeUtil.fromMillis(null));
        assertNull(TimeUtil.fromMillis(0L));
        assertNull(TimeUtil.fromMillis(-1L));
        assertEquals(0L, TimeUtil.toMillis(null));
    }
}
