package com.sent.sports.finance.repository;

import com.sent.sports.finance.domain.PaymentRecord;
import com.sent.sports.finance.mapper.PaymentRecordMapper;
import com.sent.sports.finance.mapper.po.PaymentRecordPO;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 回款单仓储实现（P5-21）：保存为「有则更新、无则新增」，更新只覆盖已核销额与状态，
 * 金额与流水号一经登记不再变动（财务口径要求）。
 * <p>银行流水号唯一性以数据库为最后一道闸：{@link #existsBySerialNo} 用于友好提示，
 * 并发下真正的拦截来自查询前的业务校验 + 人工复核，故此处不引入额外唯一索引以免阻塞历史数据迁移。
 */
@Repository
public class PaymentRecordRepositoryImpl implements PaymentRecordRepository {

    private final PaymentRecordMapper paymentRecordMapper;

    public PaymentRecordRepositoryImpl(PaymentRecordMapper paymentRecordMapper) {
        this.paymentRecordMapper = paymentRecordMapper;
    }

    @Override
    public void save(PaymentRecord record) {
        PaymentRecordPO existing = paymentRecordMapper.selectByRecordNo(record.getPaymentNo());
        PaymentRecordPO po = toPO(record);
        if (existing == null) {
            paymentRecordMapper.insert(po);
            return;
        }
        po.setId(existing.getId());
        paymentRecordMapper.updateById(po);
    }

    @Override
    public Optional<PaymentRecord> findByPaymentNo(String paymentNo) {
        PaymentRecordPO po = paymentRecordMapper.selectByRecordNo(paymentNo);
        return po == null ? Optional.empty() : Optional.of(toDomain(po));
    }

    @Override
    public boolean existsBySerialNo(String serialNo) {
        return serialNo != null && paymentRecordMapper.countBySerialNo(serialNo) > 0;
    }

    private PaymentRecordPO toPO(PaymentRecord record) {
        PaymentRecordPO po = new PaymentRecordPO();
        po.setRecordNo(record.getPaymentNo());
        po.setCustomerId(record.getCustomerId());
        po.setAmountCent(record.getAmountCent());
        po.setWrittenOffCent(record.getWrittenOffCent());
        po.setBankSerial(record.getSerialNo());
        po.setRemark(record.getRemark());
        po.setStatus(record.getStatus());
        po.setOperatorId(record.getOperatorId());
        return po;
    }

    private PaymentRecord toDomain(PaymentRecordPO po) {
        return new PaymentRecord(po.getRecordNo(), po.getCustomerId(), po.getAmountCent(),
                po.getWrittenOffCent(), po.getBankSerial(), po.getStatus(),
                po.getOperatorId() == null ? 0L : po.getOperatorId(),
                toMillis(po.getCreatedAt()), po.getRemark());
    }

    private long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    static LocalDateTime fromMillis(long millis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }
}
