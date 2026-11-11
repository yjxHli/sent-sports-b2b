package com.sent.sports.stock.service;

/**
 * 商品编辑页保存 SKU 时，经 stock 模块服务端口同步初始库存。
 * <p>跨模块写库存台账一律走本接口，product 模块不直接依赖 stock 的 mapper / domain。</p>
 */
public interface SkuStockInitService {

    /**
     * 确保 SKU 台账存在，并把物理库存同步到编辑页填写的数量；
     * 数量变化时由 stock 仓储补 ADJUST 流水，保证「台账 = 流水累计」可对账。
     */
    void syncPhysicalQty(long skuId, int physicalQty);
}
