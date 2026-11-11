package com.sent.sports.stock.service.impl;

import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.stock.service.StockRefundGateway;
import org.springframework.stereotype.Service;

/**
 * 库存退款门面实现：按订单号释放占用的库存预占。仅依赖 stock 模块内部仓储端口，符合 ArchUnit 规则 4。
 */
@Service
public class StockRefundGatewayImpl implements StockRefundGateway {

    private final StockRepository stockRepository;

    public StockRefundGatewayImpl(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    @Override
    public void releaseOnRefund(String orderNo) {
        stockRepository.releaseByOrderNo(orderNo);
    }
}
