package com.sent.sports.finance;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.finance.domain.CreditLine;
import com.sent.sports.finance.domain.PaymentRecord;
import com.sent.sports.finance.domain.PaymentReverse;
import com.sent.sports.finance.domain.Receivable;
import com.sent.sports.finance.domain.ReceivableStatus;
import com.sent.sports.finance.domain.WriteOff;
import com.sent.sports.finance.repository.CreditLineRepository;
import com.sent.sports.finance.repository.PaymentRecordRepository;
import com.sent.sports.finance.repository.PaymentReverseRepository;
import com.sent.sports.finance.repository.ReceivableRepository;
import com.sent.sports.finance.repository.WriteOffRepository;
import com.sent.sports.finance.service.ReverseRecordUseCase;
import com.sent.sports.finance.service.dto.ReverseCmd;
import com.sent.sports.finance.service.dto.ReverseResult;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 回款冲销用例测试（P3-40/41，TDD 红）：spec §6.8 / 验收——
 * 冲销回退额度（re-lock credit）、已核销先解绑应收、留痕 PaymentReverse。
 */
class ReverseRecordUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000_000L);
    private static final LocalDate DUE = LocalDate.of(2025, 12, 31);

    @Mock
    private PaymentRecordRepository paymentRecordRepository;
    @Mock
    private ReceivableRepository receivableRepository;
    @Mock
    private CreditLineRepository creditLineRepository;
    @Mock
    private WriteOffRepository writeOffRepository;
    @Mock
    private PaymentReverseRepository paymentReverseRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private Clock clock;
    @InjectMocks
    private ReverseRecordUseCase useCase;
    @Captor
    private ArgumentCaptor<Receivable> recvCaptor;
    @Captor
    private ArgumentCaptor<CreditLine> creditCaptor;
    @Captor
    private ArgumentCaptor<PaymentRecord> payCaptor;
    @Captor
    private ArgumentCaptor<WriteOff> woCaptor;
    @Captor
    private ArgumentCaptor<PaymentReverse> reverseCaptor;

    private PaymentRecord partiallyWrittenOffPayment() {
        return new PaymentRecord("PR1", 10L, 100000L, 40000L, "SN1",
                PaymentRecord.STATUS_PENDING, 7L, NOW.toEpochMilli(), "货款");
    }

    @Test
    void reversesWrittenOffPaymentUnbindsReceivableRelocksCreditAndKeepsTrail() {
        PaymentRecord payment = partiallyWrittenOffPayment();
        Receivable r1 = new Receivable(100000L, 40000L, DUE, ReceivableStatus.NORMAL);
        CreditLine credit = new CreditLine(10L, 100000L, 0L, 30L, 1L, 2L, CreditLine.STATUS_ACTIVE);
        WriteOff wo = new WriteOff("WO1", "PR1", 10L, 40000L,
                List.of(new WriteOff.Item(1L, 40000L)), NOW.toEpochMilli(), 7L);

        when(paymentRecordRepository.findByPaymentNo("PR1")).thenReturn(Optional.of(payment));
        when(writeOffRepository.findByPaymentNo("PR1")).thenReturn(List.of(wo));
        when(receivableRepository.findById(1L)).thenReturn(Optional.of(r1));
        when(creditLineRepository.findByCustomerId(10L)).thenReturn(Optional.of(credit));
        when(idGen.next("PRV")).thenReturn("PRV1");
        when(clock.instant()).thenReturn(NOW);

        ReverseResult r = useCase.reverse(new ReverseCmd("PR1", 7L, "客户退票"));

        verify(receivableRepository).save(recvCaptor.capture());
        assertEquals(0L, recvCaptor.getValue().getReceivedCent());

        verify(creditLineRepository).save(creditCaptor.capture());
        assertEquals(40000L, creditCaptor.getValue().getUsedCent());

        verify(writeOffRepository).save(woCaptor.capture());
        assertTrue(woCaptor.getValue().isVoided());

        verify(paymentRecordRepository).save(payCaptor.capture());
        assertEquals(PaymentRecord.STATUS_REVERSED, payCaptor.getValue().getStatus());
        assertEquals(0L, payCaptor.getValue().getWrittenOffCent());

        verify(paymentReverseRepository).save(reverseCaptor.capture());
        PaymentReverse rev = reverseCaptor.getValue();
        assertEquals("PR1", rev.getPaymentNo());
        assertEquals(7L, rev.getOperatorId());
        assertEquals("客户退票", rev.getReason());

        assertEquals("PR1", r.paymentNo());
        assertEquals(ReverseRecordUseCase.STATUS_REVERSED, r.status());
    }

    @Test
    void paymentNotFound() {
        when(paymentRecordRepository.findByPaymentNo("PRX")).thenReturn(Optional.empty());
        BizException e = assertThrows(BizException.class,
                () -> useCase.reverse(new ReverseCmd("PRX", 7L, "x")));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verify(paymentReverseRepository, never()).save(any());
    }

    @Test
    void alreadyReversedRejected() {
        PaymentRecord reversed = new PaymentRecord("PR1", 10L, 100000L, 0L, "SN1",
                PaymentRecord.STATUS_REVERSED, 7L, NOW.toEpochMilli(), "货款");
        when(paymentRecordRepository.findByPaymentNo("PR1")).thenReturn(Optional.of(reversed));
        BizException e = assertThrows(BizException.class,
                () -> useCase.reverse(new ReverseCmd("PR1", 7L, "x")));
        assertEquals(ErrorCode.STATE_CONFLICT, e.getErrorCode());
        verify(paymentReverseRepository, never()).save(any());
    }
}
