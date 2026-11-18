package com.sent.sports.common.util;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.List;
import java.util.Objects;

/**
 * 管理端批量操作的 ID 归一化工具（纯函数，无 Spring 依赖）。
 *
 * <p><b>为什么必须排序去重</b>：危险操作令牌绑定 {@code action|targetId}，
 * 前端换令牌与后端消费必须用同一串目标 ID。勾选顺序、重复勾选都会让原样拼接的字符串不同，
 * 导致「明明输入了删除却 40302」。两端都按「trim → 去空 → 去重 → 字典序升序」计算即可对齐。</p>
 */
public final class BatchIds {

    /** 一次最多删 200 条：防止误触全选把整库标记掉，也避免 SQL IN 参数过多。 */
    public static final int MAX_SIZE = 200;

    private BatchIds() {
    }

    /**
     * 归一化：空白归一、去重、字典序升序。空集合或超过 {@link #MAX_SIZE} 抛参数错误。
     */
    public static List<String> normalize(List<String> ids) {
        if (ids == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先选择要删除的记录");
        }
        List<String> cleaned = ids.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .toList();
        if (cleaned.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先选择要删除的记录");
        }
        if (cleaned.size() > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_INVALID, "一次最多删除 " + MAX_SIZE + " 条记录");
        }
        return cleaned;
    }

    /** 确认令牌的目标串：归一化后以英文逗号连接。 */
    public static String targetId(List<String> ids) {
        return String.join(",", normalize(ids));
    }

    /** 数字型主键专用：归一化后逐项转 Long，非法值直接拒绝，避免 MySQL 隐式转换把 "1abc" 当 1。 */
    public static List<Long> normalizeNumbers(List<String> ids) {
        return normalize(ids).stream().map(id -> {
            try {
                return Long.valueOf(id);
            } catch (NumberFormatException ex) {
                throw new BizException(ErrorCode.PARAM_INVALID, "主键必须是数字：" + id);
            }
        }).sorted().toList();
    }
}
