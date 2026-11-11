package com.sent.sports.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 金额工具（plan §4.3 / §7.6）：
 * - 分↔元格式化（金额一律以 BIGINT 分存储，展示时才换算）；
 * - 按比例分摊 {@link #allocate(long, List)}，采用"最大余数法"，保证 {@code Σ 分摊 = total}，
 *   不允许出现 1 分钱对不上（退款/优惠分摊统一走此方法）。
 */
public final class MoneyUtil {

    private MoneyUtil() {
    }

    /**
     * 分 → 元字符串（保留两位小数）。
     * @example toYuan(12345) => "123.45"
     */
    public static String toYuan(long cent) {
        boolean negative = cent < 0;
        long abs = Math.abs(cent);
        long yuan = abs / 100;
        long frac = abs % 100;
        return (negative ? "-" : "") + yuan + "." + String.format("%02d", frac);
    }

    /**
     * 元字符串 → 分（四舍五入）。支持整数与两位小数，如 "123.45"→12345、"0"→0、"-5.00"→-500。
     */
    public static long toCent(String yuan) {
        if (yuan == null || yuan.isBlank()) {
            throw new IllegalArgumentException("金额字符串为空");
        }
        BigDecimal bd = new BigDecimal(yuan.trim())
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP);
        return bd.longValueExact();
    }

    /**
     * 按权重比例分摊 {@code total}（单位：分）到各项。
     * - 权重为 0 的项恒得 0；
     * - 余数用"最大余数法"补给余数最大者（余数相同则按权重、再按原始下标），保证 {@code Σ 结果 = total}；
     * - 负权重、或"总权重为0但金额非零"均抛异常。
     *
     * @param total   待分摊总额（分）
     * @param weights 各项权重（顺序与返回结果一一对应）
     * @return 各项分摊金额（分），与 weights 等长，且求和为 total
     */
    public static List<Long> allocate(long total, List<Long> weights) {
        int n = weights.size();
        List<Long> result = new ArrayList<>(Collections.nCopies(n, 0L));

        long sumW = 0;
        for (long w : weights) {
            if (w < 0) {
                throw new IllegalArgumentException("权重不能为负数");
            }
            sumW += w;
        }
        if (sumW == 0) {
            if (total != 0) {
                throw new IllegalArgumentException("总权重为 0，无法分摊非零金额");
            }
            return result;
        }
        if (total == 0) {
            return result;
        }

        long allocated = 0;
        long[] remainder = new long[n]; // (total*w) mod sumW ∈ [0, sumW)
        for (int i = 0; i < n; i++) {
            long w = weights.get(i);
            long base = total * w / sumW;
            result.set(i, base);
            allocated += base;
            remainder[i] = total * w - base * sumW;
        }

        long left = total - allocated; // 待补发的余数（分），恒 ∈ [0, n)
        if (left > 0) {
            Integer[] idx = new Integer[n];
            for (int i = 0; i < n; i++) {
                idx[i] = i;
            }
            // 余数降序；同余数按权重降序；再按下标升序，保证确定性
            Arrays.sort(idx, (a, b) -> {
                int c = Long.compare(remainder[b], remainder[a]);
                if (c != 0) {
                    return c;
                }
                int cw = Long.compare(weights.get(b), weights.get(a));
                if (cw != 0) {
                    return cw;
                }
                return Integer.compare(a, b);
            });
            for (int k = 0; k < left; k++) {
                int i = idx[k];
                result.set(i, result.get(i) + 1);
            }
        }
        return result;
    }

    /** 变参重载，便于调用方直接传入多个权重。 */
    public static List<Long> allocate(long total, long... weights) {
        List<Long> list = new ArrayList<>(weights.length);
        for (long w : weights) {
            list.add(w);
        }
        return allocate(total, list);
    }
}
