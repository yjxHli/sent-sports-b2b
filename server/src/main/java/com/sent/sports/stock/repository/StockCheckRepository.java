package com.sent.sports.stock.repository;

import com.sent.sports.stock.domain.StockCheck;

import java.util.Optional;

/**
 * 盘点单仓储端口（P3-35 引入，实现见 Phase 5）：持久化盘点单头与单行（按 checkNo 唯一）。
 */
public interface StockCheckRepository {
    void save(StockCheck check);

    Optional<StockCheck> findByCheckId(String checkNo);
}
