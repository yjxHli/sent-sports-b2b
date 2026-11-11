package com.sent.sports.customer.repository;

import com.sent.sports.customer.domain.Customer;

import java.util.Optional;

/**
 * 客户仓储端口（P3-49 引入，实现见 Phase 5）：按客户读取与持久化客户聚合。
 */
public interface CustomerRepository {
    Optional<Customer> findByCustomerId(long customerId);

    void save(Customer customer);
}
