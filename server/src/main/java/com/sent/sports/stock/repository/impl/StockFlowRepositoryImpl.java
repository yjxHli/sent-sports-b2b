package com.sent.sports.stock.repository.impl;

import com.sent.sports.stock.domain.StockFlow;
import com.sent.sports.stock.mapper.StockFlowMapper;
import com.sent.sports.stock.mapper.po.StockFlowPO;
import com.sent.sports.stock.repository.StockFlowRepository;
import org.springframework.stereotype.Repository;

/**
 * 库存流水仓储（真库）：流水必须落库，故这里直接写 {@code stock_flow}。
 * 与 {@code StockRepositoryImpl} 共用 {@code uk_flow_biz} 唯一索引做轻量防重。
 */
@Repository
public class StockFlowRepositoryImpl implements StockFlowRepository {

    private final StockFlowMapper stockFlowMapper;

    public StockFlowRepositoryImpl(StockFlowMapper stockFlowMapper) {
        this.stockFlowMapper = stockFlowMapper;
    }

    @Override
    public void save(StockFlow flow) {
        StockFlowPO po = new StockFlowPO();
        po.setSkuId(flow.skuId());
        po.setBizType(flow.bizType());
        po.setChangeQty((int) flow.changeQty());
        po.setBeforeQty((int) flow.beforeQty());
        po.setAfterQty((int) flow.afterQty());
        po.setBizNo(flow.bizNo());
        po.setOperatorId(flow.operatorId());
        po.setRemark(flow.remark());
        stockFlowMapper.insert(po);
    }
}
