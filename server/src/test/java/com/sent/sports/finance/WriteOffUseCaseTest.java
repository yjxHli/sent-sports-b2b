package com.sent.sports.finance;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.finance.domain.CreditLine;
import com.sent.sports.finance.domain.PaymentRecord;
import com.sent.sports.finance.domain.Receivable;
import com.sent.sports.finance.domain.ReceivableStatus;
import com.sent.sports.finance.domain.WriteOff;
import com.sent.sports.finance.mapper.EnterpriseProfileMapper;
import com.sent.sports.finance.repository.CreditLineRepository;
import com.sent.sports.finance.repository.PaymentRecordRepository;
import com.sent.sports.finance.repository.ReceivableRepository;
import com.sent.sports.finance.repository.WriteOffRepository;
import com.sent.sports.finance.service.WriteOffUseCase;
import com.sent.sports.finance.service.dto.WriteOffCmd;
import com.sent.sports.finance.service.dto.WriteOffLine;
import com.sent.sports.finance.service.dto.WriteOffResult;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 核销用例测试（P3-38/39，TDD 红）：spec §6.8.4 / 验收 A9/A18——
 * 超额拒绝(WRITE_OFF_OVERFLOW)、一笔记多单、释放额度、结清状态(CLOSED)。
 */
class WriteOffUseCaseTest extends BaseUseCaseTest {

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
    private EnterpriseProfileMapper enterpriseProfileMapper;
    @Mock
    private IdGen idGen;
    @Mock
    private Clock clock;
    @InjectMocks
    private WriteOffUseCase useCase;
    @Captor
    private ArgumentCaptor<Receivable> recvCaptor;
    @Captor
    private ArgumentCaptor<CreditLine> creditCaptor;
    @Captor
    private ArgumentCaptor<PaymentRecord> payCaptor;
    @Captor
    private ArgumentCaptor<WriteOff> woCaptor;

    private PaymentRecord pendingPayment() {
        return new PaymentRecord("PR1", 10L, 100000L, 0L, "SN1",
                PaymentRecord.STATUS_PENDING, 7L, NOW.toEpochMilli(), "货款");
    }

    @Test
    void writesOffMultipleReceivablesReleasesCreditAndClosesFull() {
        PaymentRecord payment = pendingPayment();
        Receivable r1 = new Receivable(100000L, 0L, DUE, ReceivableStatus.NORMAL);
        Receivable r2 = new Receivable(40000L, 0L, DUE, ReceivableStatus.NORMAL);
        CreditLine credit = new CreditLine(10L, 200000L, 100000L, 30L, 1L, 2L, CreditLine.STATUS_ACTIVE);

        when(paymentRecordRepository.findByPaymentNo("PR1")).thenReturn(Optional.of(payment));
        when(receivableRepository.findById(1L)).thenReturn(Optional.of(r1));
        when(receivableRepository.findById(2L)).thenReturn(Optional.of(r2));
        when(creditLineRepository.findByCustomerId(10L)).thenReturn(Optional.of(credit));
        when(idGen.next("WO")).thenReturn("WO1");
        when(clock.instant()).thenReturn(NOW);

        WriteOffResult r = useCase.writeOff(new WriteOffCmd(10L, "PR1",
                List.of(new WriteOffLine(1L, 60000L), new WriteOffLine(2L, 40000L)), 7L));

        verify(receivableRepository, times(2)).save(recvCaptor.capture());
        List<Receivable> saved = recvCaptor.getAllValues();
        assertEquals(60000L, saved.get(0).getReceivedCent());
        assertEquals(40000L, saved.get(1).getReceivedCent());
        assertEquals(ReceivableStatus.CLOSED, saved.get(1).getStatus());

        verify(creditLineRepository).save(creditCaptor.capture());
        assertEquals(0L, creditCaptor.getValue().getUsedCent());

        verify(paymentRecordRepository).save(payCaptor.capture());
        assertEquals(100000L, payCaptor.getValue().getWrittenOffCent());

        verify(writeOffRepository).save(woCaptor.capture());
        assertEquals(100000L, woCaptor.getValue().getTotalCent());

        assertEquals(100000L, r.totalWrittenOff());
        assertEquals(List.of(2L), r.closedReceivableIds());
        assertEquals(WriteOffUseCase.STATUS_SUCCESS, r.status());
    }

    @Test
    void paymentNotFound() {
        when(paymentRecordRepository.findByPaymentNo("PRX")).thenReturn(Optional.empty());
        BizException e = assertThrows(BizException.class,
                () -> useCase.writeOff(new WriteOffCmd(10L, "PRX",
                        List.of(new WriteOffLine(1L, 100L)), 7L)));
        assertEquals(ErrorCode.NOT_FOUND, e.getErrorCode());
        verify(receivableRepository, never()).save(any());
    }

    @Test
    void reversedPaymentRejected() {
        PaymentRecord reversed = new PaymentRecord("PR1", 10L, 100000L, 0L, "SN1",
                PaymentRecord.STATUS_REVERSED, 7L, NOW.toEpochMilli(), "货款");
        when(paymentRecordRepository.findByPaymentNo("PR1")).thenReturn(Optional.of(reversed));
        BizException e = assertThrows(BizException.class,
                () -> useCase.writeOff(new WriteOffCmd(10L, "PR1",
                        List.of(new WriteOffLine(1L, 100L)), 7L)));
        assertEquals(ErrorCode.STATE_CONFLICT, e.getErrorCode());
        verify(receivableRepository, never()).save(any());
    }

    @Test
    void overflowOnPaymentThrows() {
        PaymentRecord payment = pendingPayment();
        Receivable r1 = new Receivable(100000L, 0L, DUE, ReceivableStatus.NORMAL);
        Receivable r2 = new Receivable(40000L, 0L, DUE, ReceivableStatus.NORMAL);
        when(paymentRecordRepository.findByPaymentNo("PR1")).thenReturn(Optional.of(payment));
        when(receivableRepository.findById(1L)).thenReturn(Optional.of(r1));
        when(receivableRepository.findById(2L)).thenReturn(Optional.of(r2));

        BizException e = assertThrows(BizException.class,
                () -> useCase.writeOff(new WriteOffCmd(10L, "PR1",
                        List.of(new WriteOffLine(1L, 60000L), new WriteOffLine(2L, 50000L)), 7L)));
        assertEquals(ErrorCode.WRITE_OFF_OVERFLOW, e.getErrorCode());
    }

    @Test
    void overflowOnReceivableThrows() {
        PaymentRecord payment = pendingPayment();
        Receivable r1 = new Receivable(10000L, 0L, DUE, ReceivableStatus.NORMAL);
        when(paymentRecordRepository.findByPaymentNo("PR1")).thenReturn(Optional.of(payment));
        when(receivableRepository.findById(1L)).thenReturn(Optional.of(r1));

        BizException e = assertThrows(BizException.class,
                () -> useCase.writeOff(new WriteOffCmd(10L, "PR1",
                        List.of(new WriteOffLine(1L, 20000L)), 7L)));
        assertEquals(ErrorCode.WRITE_OFF_OVERFLOW, e.getErrorCode());
    }
}
