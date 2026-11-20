package com.sent.sports.stock.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.service.BatchDeleteExecutor;
import com.sent.sports.stock.mapper.StockFlowMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 报损流水 / 手工入库流水：只打 soft-delete 标记，库存数量与 before/after 快照不动。 */
@Service
public class StockFlowBatchDeleteUseCase {

    private final StockFlowMapper stockFlowMapper;

    public StockFlowBatchDeleteUseCase(StockFlowMapper stockFlowMapper) {
        this.stockFlowMapper = stockFlowMapper;
    }

    @UseCase
    public int delete(List<String> ids) {
        return BatchDeleteExecutor.numeric(ids, stockFlowMapper::batchMarkDeleted);
    }
}
