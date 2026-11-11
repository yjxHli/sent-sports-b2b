package com.sent.sports.finance.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.finance.domain.PaymentRecord;
import com.sent.sports.finance.domain.Receivable;
import com.sent.sports.finance.domain.WriteOff;
import com.sent.sports.finance.domain.WriteOffAllocator;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.repository.CreditLineRepository;
import com.sent.sports.finance.repository.PaymentRecordRepository;
import com.sent.sports.finance.repository.ReceivableRepository;
import com.sent.sports.finance.repository.WriteOffRepository;
import com.sent.sports.finance.service.dto.WriteOffCmd;
import com.sent.sports.finance.service.dto.WriteOffLine;
import com.sent.sports.finance.service.dto.WriteOffResult;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 核销用例（P3-38/39）：spec §6.8.4 / 验收 A9/A18——
 * 一笔记多单；超额拒绝(WRITE_OFF_OVERFLOW)；回款后释放信用额度；应收收满 CLOSED。
 * 复用 {@link WriteOffAllocator} 做分配与超额校验。仅依赖 finance 内部与 common。
 */
@Service
public class WriteOffUseCase {

    public static final String STATUS_SUCCESS = "SUCCESS";

    private final PaymentRecordRepository paymentRecordRepository;
    private final ReceivableRepository receivableRepository;
    private final CreditLineRepository creditLineRepository;
    private final WriteOffRepository writeOffRepository;
    private final EnterpriseProfileMapper enterpriseProfileMapper;
    private final IdGen idGen;
    private final Clock clock;

    public WriteOffUseCase(PaymentRecordRepository paymentRecordRepository,
                           ReceivableRepository receivableRepository,
                           CreditLineRepository creditLineRepository,
                           WriteOffRepository writeOffRepository,
                           EnterpriseProfileMapper enterpriseProfileMapper,
                           IdGen idGen, Clock clock) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.receivableRepository = receivableRepository;
        this.creditLineRepository = creditLineRepository;
        this.writeOffRepository = writeOffRepository;
        this.enterpriseProfileMapper = enterpriseProfileMapper;
        this.idGen = idGen;
        this.clock = clock;
    }

    @UseCase
    public WriteOffResult writeOff(WriteOffCmd cmd) {
        PaymentRecord payment = paymentRecordRepository.findByPaymentNo(cmd.paymentNo())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        if (PaymentRecord.STATUS_REVERSED.equals(payment.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }

        // 载入应收台账并构建核销快照（一笔记多单）
        Map<Long, Receivable> recvById = new HashMap<>();
        List<WriteOffAllocator.WriteOffItem> allocItems = new ArrayList<>();
        for (WriteOffLine line : cmd.lines()) {
            Receivable r = receivableRepository.findById(line.receivableId())
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
            recvById.put(line.receivableId(), r);
            allocItems.add(new WriteOffAllocator.WriteOffItem(line.receivableId(), line.amount(),
                    new WriteOffAllocator.ReceivableSnapshot(r.getAmountCent(), r.getReceivedCent())));
        }

        WriteOffAllocator.PaymentRecord pr =
                new WriteOffAllocator.PaymentRecord(payment.getAmountCent(), payment.getWrittenOffCent());
        WriteOffAllocator.WriteOffResult alloc = WriteOffAllocator.allocate(pr, allocItems);

        // 应用分配：累计已收、收满 CLOSED，并持久化应收
        List<Long> closedIds = new ArrayList<>();
        List<WriteOff.Item> persistedItems = new ArrayList<>();
        for (WriteOffAllocator.AllocatedReceivable a : alloc.items()) {
            Receivable r = recvById.get(a.receivableId());
            long assign = a.newReceivedCent() - r.getReceivedCent();
            r.receive(assign);
            if (a.closed()) {
                closedIds.add(a.receivableId());
            }
            receivableRepository.save(r);
            persistedItems.add(new WriteOff.Item(a.receivableId(), assign));
        }

        // 付款单累加已核销
        payment.writeOff(alloc.totalWrittenOff());
        paymentRecordRepository.save(payment);

        // 释放信用额度（冲销时会重新占用）：域对象与 enterprise_profile 真库列对称回写
        creditLineRepository.findByCustomerId(cmd.customerId()).ifPresent(c -> {
            c.release(alloc.totalWrittenOff());
            creditLineRepository.save(c);
        });
        enterpriseProfileMapper.subtractCreditUsed(cmd.customerId(), alloc.totalWrittenOff());

        long now = clock.instant().toEpochMilli();
        WriteOff wo = new WriteOff(idGen.next("WO"), cmd.paymentNo(), cmd.customerId(),
                alloc.totalWrittenOff(), persistedItems, now, cmd.operatorId());
        writeOffRepository.save(wo);

        return new WriteOffResult(cmd.paymentNo(), alloc.totalWrittenOff(), closedIds, STATUS_SUCCESS);
    }
}
