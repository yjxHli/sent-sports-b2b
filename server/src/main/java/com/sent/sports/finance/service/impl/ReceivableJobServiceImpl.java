package com.sent.sports.finance.service.impl;

import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.mapper.po.OverdueReceivableRow;
import com.sent.sports.finance.mapper.po.ReceivablePO;
import com.sent.sports.finance.service.ReceivableJobService;
import com.sent.sports.finance.service.dto.OverdueReceivable;
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
    public List<OverdueReceivable> findOverdueDetails(LocalDate today) {
        return receivableMapper.selectOverdueDetails(today).stream()
                .filter(row -> !OVERDUE.equals(row.getStatus()))
                .map(row -> new OverdueReceivable(
                        row.getId() == null ? 0L : row.getId(),
                        row.getCustomerId() == null ? 0L : row.getCustomerId(),
                        row.getAmountCent() == null ? 0L : row.getAmountCent(),
                        row.getDueDate(),
                        row.getOrderNo() == null ? "" : row.getOrderNo()))
                .toList();
    }

    @Override
    public int markOverdue(long receivableId) {
        return receivableMapper.updateStatus(receivableId, OVERDUE);
    }
}
