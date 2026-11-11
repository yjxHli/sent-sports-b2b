package com.sent.sports.finance.repository;

import com.sent.sports.finance.domain.WriteOff;
import com.sent.sports.finance.mapper.WriteOffMapper;
import com.sent.sports.finance.mapper.po.WriteOffPO;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 核销仓储实现（P5-21）：一次核销写多行明细（每笔应收一行），共享 {@code write_off_no}；
 * 反查时按该号分组还原 {@link WriteOff} 聚合，保证「一次核销」在读取侧仍是整体，冲销可整组解绑。
 */
@Repository
public class WriteOffRepositoryImpl implements WriteOffRepository {

    private final WriteOffMapper writeOffMapper;

    public WriteOffRepositoryImpl(WriteOffMapper writeOffMapper) {
        this.writeOffMapper = writeOffMapper;
    }

    @Override
    public void save(WriteOff writeOff) {
        for (WriteOff.Item item : writeOff.getItems()) {
            WriteOffPO po = new WriteOffPO();
            po.setWriteOffNo(writeOff.getId());
            po.setPaymentRecordNo(writeOff.getPaymentNo());
            po.setReceivableId(item.receivableId());
            po.setCustomerId(writeOff.getCustomerId());
            po.setAmountCent(item.amount());
            po.setOperatorId(writeOff.getOperatorId());
            writeOffMapper.insert(po);
        }
    }

    @Override
    public List<WriteOff> findByPaymentNo(String paymentNo) {
        Map<String, List<WriteOffPO>> grouped = new LinkedHashMap<>();
        for (WriteOffPO po : writeOffMapper.selectByPaymentNo(paymentNo)) {
            grouped.computeIfAbsent(po.getWriteOffNo(), key -> new ArrayList<>()).add(po);
        }
        List<WriteOff> result = new ArrayList<>(grouped.size());
        for (Map.Entry<String, List<WriteOffPO>> entry : grouped.entrySet()) {
            result.add(toDomain(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private WriteOff toDomain(String writeOffNo, List<WriteOffPO> rows) {
        WriteOffPO first = rows.get(0);
        List<WriteOff.Item> items = new ArrayList<>(rows.size());
        long total = 0L;
        for (WriteOffPO row : rows) {
            items.add(new WriteOff.Item(row.getReceivableId(), row.getAmountCent()));
            total += row.getAmountCent();
        }
        return new WriteOff(writeOffNo, first.getPaymentRecordNo(), first.getCustomerId(), total, items,
                toMillis(first.getCreatedAt()), first.getOperatorId() == null ? 0L : first.getOperatorId());
    }

    private long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    static LocalDateTime fromMillis(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
