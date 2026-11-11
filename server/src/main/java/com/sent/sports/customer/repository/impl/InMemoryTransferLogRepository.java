package com.sent.sports.customer.repository.impl;

import com.sent.sports.customer.domain.TransferLog;
import com.sent.sports.customer.repository.TransferLogRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 客户改派日志（进程内适配器；接真库时映射 V3 的 customer_owner_log）。 */
@Repository
public class InMemoryTransferLogRepository implements TransferLogRepository {

    private final List<TransferLog> logs = new CopyOnWriteArrayList<>();

    @Override
    public void save(TransferLog log) {
        logs.add(log);
    }

    public List<TransferLog> all() {
        return List.copyOf(logs);
    }
}
