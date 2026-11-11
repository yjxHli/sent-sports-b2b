package com.sent.sports.finance.repository;

import com.sent.sports.finance.domain.CreditLine;

import java.util.Optional;

/**
 * 授信额度仓储端口（P3-42/43）：企业授信的创建、调整与读取。
 * 实现见 Phase 5。
 */
public interface CreditLineRepository {
    void save(CreditLine creditLine);

    Optional<CreditLine> findByCustomerId(long customerId);
}
