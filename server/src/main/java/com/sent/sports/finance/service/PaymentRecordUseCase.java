package com.sent.sports.finance.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.finance.domain.PaymentRecord;
import com.sent.sports.finance.repository.PaymentRecordRepository;
import com.sent.sports.finance.service.dto.PaymentRecordCmd;
import com.sent.sports.finance.service.dto.PaymentRecordResult;

import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * 登记回款用例（P3-36/37）：spec §6.8 / 验收——
 * 金额>0、客户必填、流水号唯一；创建回款单并落库（状态 PENDING）。
 * 仅依赖 finance 内部仓储端口与 common 工具，符合 ArchUnit 规则 4。
 */
@Service
public class PaymentRecordUseCase {

    private final PaymentRecordRepository paymentRecordRepository;
    private final IdGen idGen;
    private final Clock clock;

    public PaymentRecordUseCase(PaymentRecordRepository paymentRecordRepository, IdGen idGen, Clock clock) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.idGen = idGen;
        this.clock = clock;
    }

    @UseCase
    public PaymentRecordResult record(PaymentRecordCmd cmd) {
        if (cmd.amountCent() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (cmd.customerId() <= 0) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (paymentRecordRepository.existsBySerialNo(cmd.serialNo())) {
            throw new BizException(ErrorCode.BIZ_REJECT);
        }
        long now = clock.instant().toEpochMilli();
        String no = idGen.next("PR");
        PaymentRecord rec = new PaymentRecord(no, cmd.customerId(), cmd.amountCent(), 0L,
                cmd.serialNo(), PaymentRecord.STATUS_PENDING, cmd.operatorId(), now, cmd.remark());
        paymentRecordRepository.save(rec);
        return new PaymentRecordResult(no, cmd.customerId(), cmd.amountCent(), cmd.serialNo(),
                PaymentRecord.STATUS_PENDING, now, cmd.remark());
    }
}
