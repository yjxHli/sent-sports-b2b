package com.sent.sports.stock.service.impl;

import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.stock.service.StockService;
import org.springframework.stereotype.Service;

/** 库存服务实现：仅委托 stock 模块内部仓储，作为跨模块唯一入口。 */
@Service
public class StockServiceImpl implements StockService {

    private final StockRepository stockRepository;

    public StockServiceImpl(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Override
    public void lock(long skuId, int qty) {
        stockRepository.lock(skuId, qty);
    }

    @Override
    public void release(long skuId, int qty) {
        stockRepository.release(skuId, qty);
    }

    @Override
    public void releaseByOrderNo(String orderNo) {
        stockRepository.releaseByOrderNo(orderNo);
    }
}
