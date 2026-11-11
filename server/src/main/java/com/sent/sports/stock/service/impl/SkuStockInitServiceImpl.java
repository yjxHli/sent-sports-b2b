package com.sent.sports.stock.service.impl;

import com.sent.sports.stock.domain.StockItem;
import com.sent.sports.stock.mapper.StockMapper;
import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.stock.service.SkuStockInitService;
import org.springframework.stereotype.Service;

/**
 * 商品编辑页初始库存同步：先幂等建台账，再以绝对值覆盖物理库存。
 * <p>不直接写 {@code sku_stock}，复用 {@link StockRepository#save} 的「差值补流水」口径；
 * 目标数量不会低于已锁定 / 待出 / 冻结量，避免把可售库存压成负数。</p>
 */
@Service
public class SkuStockInitServiceImpl implements SkuStockInitService {

    private final StockMapper stockMapper;
    private final StockRepository stockRepository;

    public SkuStockInitServiceImpl(StockMapper stockMapper, StockRepository stockRepository) {
        this.stockMapper = stockMapper;
        this.stockRepository = stockRepository;
    }

    @Override
    public void syncPhysicalQty(long skuId, int physicalQty) {
        stockMapper.insertIfAbsent(skuId, 0);
        StockItem current = stockRepository.findBySku(skuId)
                .orElseGet(() -> new StockItem(0L, 0L, 0L, 0L));
        long reserved = current.getLockedQty() + current.getPendingQty() + current.getFrozenQty();
        long target = Math.max(Math.max(0, physicalQty), reserved);
        stockRepository.save(skuId, new StockItem(target, current.getLockedQty(),
                current.getPendingQty(), current.getFrozenQty()));
    }
}
