package com.sent.sports.stock.service;

/**
 * 库存服务（跨模块契约）：预占、释放、按订单整单释放。
 * 供 order 模块在下单/取消/关单时使用，替代跨模块直连 {@code StockRepository}（ArchUnit 规则 4/6）。
 * 仓储实现仍留在 stock 模块内部，持久化细节不外泄。
 */
public interface StockService {

    /** 预占库存；不足时由 stock 模块抛 STOCK_NOT_ENOUGH(40910)。 */
    void lock(long skuId, int qty);

    /** 释放指定 SKU 的预占额度。 */
    void release(long skuId, int qty);

    /** 按订单号释放该单全部预占（关单/退款场景）。 */
    void releaseByOrderNo(String orderNo);
}
