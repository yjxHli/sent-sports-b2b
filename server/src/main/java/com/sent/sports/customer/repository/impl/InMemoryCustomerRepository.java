package com.sent.sports.customer.repository.impl;

import com.sent.sports.customer.domain.Customer;
import com.sent.sports.customer.repository.CustomerRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 客户档案仓储（进程内适配器；接真库时映射 V3 的 customer）。 */
@Repository
public class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<Long, Customer> byCustomerId = new ConcurrentHashMap<>();

    @Override
    public Optional<Customer> findByCustomerId(long customerId) {
        return Optional.ofNullable(byCustomerId.get(customerId));
    }

    @Override
    public void save(Customer customer) {
        byCustomerId.put(customer.getCustomerId(), customer);
    }
}
