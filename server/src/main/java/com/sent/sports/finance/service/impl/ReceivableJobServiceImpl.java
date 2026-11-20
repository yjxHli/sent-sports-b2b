package com.sent.sports.finance.service.impl;

import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.mapper.po.ReceivablePO;
import com.sent.sports.finance.service.ReceivableJobService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class ReceivableJobServiceImpl implements ReceivableJobService {

    private static final String OVERDUE = "OVERDUE";

    private final ReceivableMapper receivableMapper;

    public ReceivableJobServiceImpl(ReceivableMapper receivableMapper) {
        this.receivableMapper = receivableMapper;
    }

    @Override
    public List<Long> findOverdueIds(LocalDate today) {
        return receivableMapper.selectOverdue(today).stream()
                .filter(po -> !OVERDUE.equals(po.getStatus())) // 已是 OVERDUE 的跳过，保证幂等
                .map(ReceivablePO::getId).toList();
    }

    @Override
    public int markOverdue(long receivableId) {
        return receivableMapper.updateStatus(receivableId, OVERDUE);
    }
}
