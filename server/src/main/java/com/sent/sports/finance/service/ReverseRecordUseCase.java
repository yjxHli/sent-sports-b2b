package com.sent.sports.finance.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.finance.domain.CreditLine;
import com.sent.sports.finance.domain.PaymentRecord;
import com.sent.sports.finance.domain.PaymentReverse;
import com.sent.sports.finance.domain.Receivable;
import com.sent.sports.finance.domain.WriteOff;
import com.sent.sports.finance.repository.CreditLineRepository;
import com.sent.sports.finance.repository.PaymentRecordRepository;
import com.sent.sports.finance.repository.PaymentReverseRepository;
import com.sent.sports.finance.repository.ReceivableRepository;
import com.sent.sports.finance.repository.WriteOffRepository;
import com.sent.sports.finance.service.dto.ReverseCmd;
import com.sent.sports.finance.service.dto.ReverseResult;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 回款冲销用例（P3-40/41）：spec §6.8 / 验收——
 * 已核销先解绑应收（Receivable.unreceive + 重新算状态，回退占用后重开）、
 * 回退额度（重新占用信用）、留痕 PaymentReverse；付款单置 REVERSED 并清零已核销。
 * 仅依赖 finance 内部与 common。
 */
@Service
public class ReverseRecordUseCase {

    public static final String STATUS_REVERSED = "REVERSED";

    private final PaymentRecordRepository paymentRecordRepository;
    private final ReceivableRepository receivableRepository;
    private final CreditLineRepository creditLineRepository;
    private final WriteOffRepository writeOffRepository;
    private final PaymentReverseRepository paymentReverseRepository;
    private final IdGen idGen;
    private final Clock clock;

    public ReverseRecordUseCase(PaymentRecordRepository paymentRecordRepository,
                               ReceivableRepository receivableRepository,
                               CreditLineRepository creditLineRepository,
                               WriteOffRepository writeOffRepository,
                               PaymentReverseRepository paymentReverseRepository,
                               IdGen idGen, Clock clock) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.receivableRepository = receivableRepository;
        this.creditLineRepository = creditLineRepository;
        this.writeOffRepository = writeOffRepository;
        this.paymentReverseRepository = paymentReverseRepository;
        this.idGen = idGen;
        this.clock = clock;
    }

    @UseCase
    public ReverseResult reverse(ReverseCmd cmd) {
        PaymentRecord payment = paymentRecordRepository.findByPaymentNo(cmd.paymentNo())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        if (PaymentRecord.STATUS_REVERSED.equals(payment.getStatus())) {
            throw new BizException(ErrorCode.STATE_CONFLICT);
        }

        long totalWrittenOff = 0;
        if (payment.getWrittenOffCent() > 0) {
            // 解绑该付款单的全部核销，回退应收已收额并重新计算状态
            List<WriteOff> wos = writeOffRepository.findByPaymentNo(cmd.paymentNo());
            for (WriteOff wo : wos) {
                for (WriteOff.Item item : wo.getItems()) {
                    Receivable r = receivableRepository.findById(item.receivableId())
                            .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
                    r.unreceive(item.amount());
                    r.refreshStatus(LocalDate.now());
                    receivableRepository.save(r);
                }
                wo.setVoided(true);
                writeOffRepository.save(wo);
                totalWrittenOff += wo.getTotalCent();
            }
            // 冲销 = 此前释放的额度重新被占用
            final long toRelock = totalWrittenOff;
            creditLineRepository.findByCustomerId(payment.getCustomerId()).ifPresent(c -> {
                c.consume(toRelock);
                creditLineRepository.save(c);
            });
        }

        payment.reverse();
        paymentRecordRepository.save(payment);

        long now = clock.instant().toEpochMilli();
        PaymentReverse rev = new PaymentReverse(idGen.next("PRV"), cmd.paymentNo(),
                cmd.operatorId(), cmd.reason(), now);
        paymentReverseRepository.save(rev);

        return new ReverseResult(cmd.paymentNo(), STATUS_REVERSED);
    }
}
