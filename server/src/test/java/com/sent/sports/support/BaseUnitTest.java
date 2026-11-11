package com.sent.sports.support;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 单测基类（P1-20）：提供 Mockito 扩展与固定时钟。
 * 固定时钟统一为 UTC 2024-01-01T00:00:00Z，避免用例间因"当前时间"漂移导致断言不稳定。
 */
@ExtendWith(MockitoExtension.class)
public abstract class BaseUnitTest {

    /** 固定时钟：2024-01-01T00:00:00Z（Asia/Shanghai 为 08:00） */
    protected static final Instant FIXED_INSTANT = Instant.parse("2024-01-01T00:00:00Z");
    protected static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");
    protected static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    /** 获取固定时钟（默认 UTC，可按需转换时区）。 */
    protected Clock fixedClock() {
        return FIXED_CLOCK;
    }

    protected Clock fixedClock(ZoneId zone) {
        return Clock.fixed(FIXED_INSTANT, zone);
    }
}
