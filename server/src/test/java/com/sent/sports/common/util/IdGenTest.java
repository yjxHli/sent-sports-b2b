package com.sent.sports.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IdGen 单测：校验单号格式与序列唯一性（common.util 在 JaCoCo 门禁范围内，须覆盖）。
 */
class IdGenTest {

    @Test
    void orderNoHasPrefixAndFixedLength() {
        String no = new IdGen().nextOrderNo();
        assertTrue(no.startsWith("SO"));
        // SO(2) + yyyyMMdd(8) + seq(6) = 16
        assertEquals(16, no.length());
    }

    @Test
    void consecutiveNumbersAreUnique() {
        IdGen gen = new IdGen();
        String a = gen.nextOrderNo();
        String b = gen.nextOrderNo();
        assertNotEquals(a, b);
    }
}
