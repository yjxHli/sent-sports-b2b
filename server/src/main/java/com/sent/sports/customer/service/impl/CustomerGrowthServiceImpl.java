package com.sent.sports.customer.service.impl;

import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.mapper.GrowthLogMapper;
import com.sent.sports.customer.mapper.po.CustomerPO;
import com.sent.sports.customer.service.CustomerGrowthService;
import org.springframework.stereotype.Service;

/**
 * 成长值真实落账：先由 {@code uk_growth_source} 抢到「首次发放」的唯一名额，
 * 再累加 {@code customer.growth_value}。两步同处调用方（{@code @UseCase}）的事务。
 */
@Service
public class CustomerGrowthServiceImpl implements CustomerGrowthService {

    private final CustomerMapper customerMapper;
    private final GrowthLogMapper growthLogMapper;

    public CustomerGrowthServiceImpl(CustomerMapper customerMapper, GrowthLogMapper growthLogMapper) {
        this.customerMapper = customerMapper;
        this.growthLogMapper = growthLogMapper;
    }

    @Override
    public long grantGrowth(long customerId, long delta, String sourceType, String sourceId) {
        CustomerPO customer = customerMapper.selectById(customerId);
        if (customer == null) {
            // 没有客户主记录时不制造孤立流水（集成测试里造的纯订单场景会走这里）
            return 0L;
        }
        long before = customer.getGrowthValue() == null ? 0L : customer.getGrowthValue();
        long after = before + delta;
        int inserted = growthLogMapper.insertIfAbsent(customerId, delta, after, sourceType, sourceId);
        if (inserted == 0) {
            return before; // 同一来源重放，不重复累加
        }
        customerMapper.addGrowth(customerId, delta);
        return after;
    }
}
