package com.sent.sports.finance.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.service.BatchDeleteExecutor;
import com.sent.sports.finance.mapper.PaymentRecordMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 回款记录：按回款单号软删，核销明细与冲销记录独立管理。 */
@Service
public class PaymentRecordBatchDeleteUseCase {

    private final PaymentRecordMapper paymentRecordMapper;

    public PaymentRecordBatchDeleteUseCase(PaymentRecordMapper paymentRecordMapper) {
        this.paymentRecordMapper = paymentRecordMapper;
    }

    @UseCase
    public int delete(List<String> ids) {
        return BatchDeleteExecutor.strings(ids, paymentRecordMapper::batchMarkDeleted);
    }
}
