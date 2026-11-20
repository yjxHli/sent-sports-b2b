package com.sent.sports.stock.service.impl;

import com.sent.sports.stock.mapper.StockMapper;
import com.sent.sports.stock.service.StockJobQueryService;
import org.springframework.stereotype.Service;

@Service
public class StockJobQueryServiceImpl implements StockJobQueryService {

    private final StockMapper stockMapper;

    public StockJobQueryServiceImpl(StockMapper stockMapper) {
        this.stockMapper = stockMapper;
    }

    @Override
    public int countBelowSafety() {
        return stockMapper.selectBelowSafety().size();
    }
}
