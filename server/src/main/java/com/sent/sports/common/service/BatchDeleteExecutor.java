package com.sent.sports.common.service;

import com.sent.sports.common.util.BatchIds;

import java.util.List;
import java.util.function.Function;

/**
 * 管理端批量删除的共用执行器：只负责 ID 归一化 + 转类型，真正的标记写入由各模块 mapper 完成。
 * <p>不碰任何业务字段，避免把批量删除逻辑复制成 10 份。</p>
 */
public final class BatchDeleteExecutor {

    private BatchDeleteExecutor() {
    }

    /** 数字主键（customer / 台账 / 流水 / 日志）。 */
    public static int numeric(List<String> ids, Function<List<Long>, Integer> marker) {
        return marker.apply(BatchIds.normalizeNumbers(ids));
    }

    /** 单号主键（回款记录）。 */
    public static int strings(List<String> ids, Function<List<String>, Integer> marker) {
        return marker.apply(BatchIds.normalize(ids));
    }
}
