package com.sent.sports.finance.repository.impl;

import com.sent.sports.finance.domain.CreditLine;
import com.sent.sports.finance.repository.CreditLineRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 授信额度仓储（进程内适配器；接真库时映射 V3 的 customer.credit_* 列）。 */
@Repository
public class InMemoryCreditLineRepository implements CreditLineRepository {

    private final Map<Long, CreditLine> byCustomerId = new ConcurrentHashMap<>();

    @Override
    public void save(CreditLine creditLine) {
        byCustomerId.put(creditLine.getCustomerId(), creditLine);
    }

    @Override
    public Optional<CreditLine> findByCustomerId(long customerId) {
        return Optional.ofNullable(byCustomerId.get(customerId));
    }
}
