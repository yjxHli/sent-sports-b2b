package com.sent.sports.stock.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.util.BatchIds;
import com.sent.sports.stock.mapper.StockMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端库存列表批量删除：把 SKU 打上 {@code sku.deleted = 1} 并置为下架，
 * 列表与 count 同步隐藏；库存台账 {@code sku_stock} 的实物/锁定数量不动，账实仍可对。
 */
@Service
public class StockBatchDeleteUseCase {

    private final StockMapper stockMapper;

    public StockBatchDeleteUseCase(StockMapper stockMapper) {
        this.stockMapper = stockMapper;
    }

    @UseCase
    public int delete(List<String> ids) {
        return stockMapper.batchMarkDeleted(BatchIds.normalizeNumbers(ids));
    }
}
