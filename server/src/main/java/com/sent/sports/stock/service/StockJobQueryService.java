package com.sent.sports.stock.service;

/** 定时任务专用的库存只读查询口：只回数量，PO 留在 stock 模块内。 */
public interface StockJobQueryService {

    /** 低于安全库存的 SKU 数量。 */
    int countBelowSafety();
}
