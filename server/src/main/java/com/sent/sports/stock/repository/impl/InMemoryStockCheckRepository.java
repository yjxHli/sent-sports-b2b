package com.sent.sports.stock.repository.impl;

import com.sent.sports.stock.domain.StockCheck;
import com.sent.sports.stock.repository.StockCheckRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 盘点单仓储（进程内适配器；接真库时映射 V5 的 stock_check / stock_check_item）。 */
@Repository
public class InMemoryStockCheckRepository implements StockCheckRepository {

    private final Map<String, StockCheck> byCheckNo = new ConcurrentHashMap<>();

    @Override
    public void save(StockCheck check) {
        byCheckNo.put(check.getCheckNo(), check);
    }

    @Override
    public Optional<StockCheck> findByCheckId(String checkNo) {
        return Optional.ofNullable(byCheckNo.get(checkNo));
    }
}
