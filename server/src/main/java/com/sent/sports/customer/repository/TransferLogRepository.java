package com.sent.sports.customer.repository;

import com.sent.sports.customer.domain.TransferLog;

/**
 * 客户流转日志仓储端口（P3-49 引入，实现见 Phase 5）：持久化改派留痕。
 */
public interface TransferLogRepository {
    void save(TransferLog log);
}
