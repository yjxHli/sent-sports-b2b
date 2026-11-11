package com.sent.sports.finance.repository;

import com.sent.sports.finance.domain.Receivable;

import java.util.List;
import java.util.Optional;

/**
 * 应收仓储端口（P3-38/39）：核销时读取与持久化应收台账。
 * 实现见 Phase 5。
 */
public interface ReceivableRepository {
    void save(Receivable receivable);

    Optional<Receivable> findById(long id);

    List<Receivable> findAllByCustomerId(long customerId);
}
