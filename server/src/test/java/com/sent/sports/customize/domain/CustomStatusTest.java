package com.sent.sports.customize.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** P2-23 定制状态枚举测试（TDD 红）：10 个状态含 ESCALATED。 */
class CustomStatusTest {

    @Test
    void tenStatuses() {
        assertEquals(10, CustomStatus.values().length);
    }

    @Test
    void keyNames() {
        assertEquals("审核通过", CustomStatus.APPROVED.getChineseName());
        assertEquals("待主管处理", CustomStatus.ESCALATED.getChineseName());
        assertEquals("生产中", CustomStatus.IN_PRODUCTION.getChineseName());
    }

    @Test
    void escalatedExists() {
        assertTrue(java.util.Arrays.stream(CustomStatus.values())
                .anyMatch(s -> s == CustomStatus.ESCALATED));
    }
}
