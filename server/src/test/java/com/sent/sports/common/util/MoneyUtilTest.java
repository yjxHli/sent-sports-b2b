package com.sent.sports.common.util;

import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-01 金额分摊测试（TDD 红）：
 * 覆盖 比例分摊 / 余数归属(最大余数法) / 单行 / 0 权重 / 合计恒等 / 负权重异常 / 分↔元格式化。
 */
class MoneyUtilTest extends BaseUnitTest {

    /** 比例分摊：权重 1:2:1 → 25:50:25 */
    @Test
    void allocateByRatio() {
        List<Long> r = MoneyUtil.allocate(100L, List.of(1L, 2L, 1L));
        assertEquals(List.of(25L, 50L, 25L), r);
    }

    /** 余数归属：total=11 两份均分，余数 1 补给余数最大者（首份）→ [6,5] 且 Σ=11 */
    @Test
    void allocateRemainderToLargest() {
        List<Long> r = MoneyUtil.allocate(11L, List.of(1L, 1L));
        assertEquals(11L, r.stream().mapToLong(Long::longValue).sum());
        assertEquals(List.of(6L, 5L), r);
    }

    /** 余数归属(三份)：total=7 权重 2:2:2 → 每份 base=2 余 1，补给首份 → [3,2,2]，Σ=7 */
    @Test
    void allocateRemainderThreeWay() {
        List<Long> r = MoneyUtil.allocate(7L, List.of(2L, 2L, 2L));
        assertEquals(List.of(3L, 2L, 2L), r);
        assertEquals(7L, r.stream().mapToLong(Long::longValue).sum());
    }

    /** 单行：整笔归唯一一项 */
    @Test
    void allocateSingleRow() {
        List<Long> r = MoneyUtil.allocate(12345L, List.of(1L));
        assertEquals(List.of(12345L), r);
    }

    /** 0 权重：零权重项恒得 0，且不吸收余数 */
    @Test
    void allocateZeroWeightStaysZero() {
        // total=101, 权重 0:1:1 → [0,51,50]，零权重项不得拿到余数
        List<Long> r = MoneyUtil.allocate(101L, List.of(0L, 1L, 1L));
        assertEquals(List.of(0L, 51L, 50L), r);
        assertEquals(101L, r.stream().mapToLong(Long::longValue).sum());
    }

    /** 合计恒等：多组用例 Σ 必须等于 total */
    @Test
    void allocateSumIdentity() {
        long[][] cases = {
                {1000, 3, 3, 4},
                {999, 7, 11, 13, 17},
                {1, 1, 1, 1, 1, 1, 1, 1, 1, 1},
                {250, 0, 5, 5, 0, 10},
                {77, 100, 1}
        };
        for (long[] c : cases) {
            long total = c[0];
            List<Long> weights = new java.util.ArrayList<>();
            for (int i = 1; i < c.length; i++) weights.add(c[i]);
            List<Long> r = MoneyUtil.allocate(total, weights);
            assertEquals(total, r.stream().mapToLong(Long::longValue).sum(),
                    "合计必须等于 total=" + total);
            // 每一项非负
            r.forEach(x -> assertTrue(x >= 0, "分摊结果不得为负"));
        }
    }

    /** 全额为 0：各项均为 0 */
    @Test
    void allocateZeroTotal() {
        List<Long> r = MoneyUtil.allocate(0L, List.of(1L, 2L, 3L));
        assertEquals(List.of(0L, 0L, 0L), r);
    }

    /** 负权重应抛异常 */
    @Test
    void allocateNegativeWeightRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MoneyUtil.allocate(100L, List.of(1L, -1L)));
    }

    /** 总权重为 0 且金额非零：无法分摊，抛异常 */
    @Test
    void allocateAllZeroWeightWithNonZeroTotalRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MoneyUtil.allocate(100L, List.of(0L, 0L)));
    }

    /** 分→元格式化 */
    @Test
    void toYuan() {
        assertEquals("123.45", MoneyUtil.toYuan(12345L));
        assertEquals("0.00", MoneyUtil.toYuan(0L));
        assertEquals("-5.00", MoneyUtil.toYuan(-500L));
        assertEquals("1000.00", MoneyUtil.toYuan(100000L));
    }

    /** 元→分解析 */
    @Test
    void toCent() {
        assertEquals(12345L, MoneyUtil.toCent("123.45"));
        assertEquals(0L, MoneyUtil.toCent("0"));
        assertEquals(-500L, MoneyUtil.toCent("-5.00"));
    }
}
