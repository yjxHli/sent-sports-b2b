package com.sent.sports.customer.repository;

import com.sent.sports.customer.domain.TransferLog;

import java.util.List;

/**
 * 客户流转日志仓储端口（P3-49 引入，2026-11-28 起实现落真库）：持久化改派留痕。
 */
public interface TransferLogRepository {

    void save(TransferLog log);

    /**
     * 按客户查改派历史，最新在前。
     *
     * <p>补这个方法是为了让「落库」这件事可被验证：只写不读的仓储无法证明重启后数据还在
     * （原来的内存实现也只有 {@code save}，另有个非接口的 {@code all()}）。
     */
    List<TransferLog> findByCustomerId(long customerId);
}
