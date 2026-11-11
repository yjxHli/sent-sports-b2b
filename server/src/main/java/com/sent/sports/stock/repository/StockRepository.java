package com.sent.sports.stock.repository;

import com.sent.sports.stock.domain.StockItem;

import java.util.Optional;

/**
 * 库存仓储端口：预占 / 释放。预占不足时由实现层抛 STOCK_NOT_ENOUGH(40910)。
 * 实现见 Phase 5；用例层据此在预占失败时整单回滚。
 */
public interface StockRepository {
    void lock(long skuId, int qty);

    void release(long skuId, int qty);

    void releaseByOrderNo(String orderNo);

    /** 按 SKU 读取库存台账（无记录返回 empty）。 */
    Optional<StockItem> findBySku(long skuId);

    /** 持久化某 SKU 的库存台账。 */
    void save(long skuId, StockItem item);
}
