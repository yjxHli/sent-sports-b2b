package com.sent.sports.stock.repository;

import com.sent.sports.stock.domain.StockFlow;

/**
 * 库存流水仓储端口（P3-31 引入，实现见 Phase 5）：持久化每一次库存变动流水。
 */
public interface StockFlowRepository {
    void save(StockFlow flow);
}
