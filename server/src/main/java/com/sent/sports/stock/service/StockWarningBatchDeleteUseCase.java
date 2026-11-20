package com.sent.sports.stock.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.service.BatchDeleteExecutor;
import com.sent.sports.stock.mapper.StockMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 库存预警清单：只标记台账不再展示，SKU 与实物库存一概不动。 */
@Service
public class StockWarningBatchDeleteUseCase {

    private final StockMapper stockMapper;

    public StockWarningBatchDeleteUseCase(StockMapper stockMapper) {
        this.stockMapper = stockMapper;
    }

    @UseCase
    public int delete(List<String> ids) {
        return BatchDeleteExecutor.numeric(ids, stockMapper::batchMarkWarningDeleted);
    }
}
