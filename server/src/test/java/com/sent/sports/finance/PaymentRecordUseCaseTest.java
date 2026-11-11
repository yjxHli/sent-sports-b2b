package com.sent.sports.finance;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.finance.domain.PaymentRecord;
import com.sent.sports.finance.repository.PaymentRecordRepository;
import com.sent.sports.finance.service.PaymentRecordUseCase;
import com.sent.sports.finance.service.dto.PaymentRecordCmd;
import com.sent.sports.finance.service.dto.PaymentRecordResult;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登记回款用例测试（P3-36/37，TDD 红）：spec §6.8 / 验收——
 * 金额>0、客户必填、流水号唯一；创建回款单并落库。
 */
class PaymentRecordUseCaseTest extends BaseUseCaseTest {

    private static final Instant NOW = Instant.ofEpochMilli(1_000_000L);

    @Mock
    private PaymentRecordRepository paymentRecordRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private Clock clock;
    @InjectMocks
    private PaymentRecordUseCase useCase;
    @Captor
    private ArgumentCaptor<PaymentRecord> recordCaptor;

    @Test
    void recordsPaymentWhenValid() {
        when(paymentRecordRepository.existsBySerialNo("SN001")).thenReturn(false);
        when(idGen.next("PR")).thenReturn("PR20240101000001");
        when(clock.instant()).thenReturn(NOW);

        PaymentRecordResult r = useCase.record(new PaymentRecordCmd(10L, 50000L, "SN001", 7L, "货款"));

        verify(paymentRecordRepository).save(recordCaptor.capture());
        PaymentRecord saved = recordCaptor.getValue();
        assertEquals("PR20240101000001", saved.getPaymentNo());
        assertEquals(10L, saved.getCustomerId());
        assertEquals(50000L, saved.getAmountCent());
        assertEquals(0L, saved.getWrittenOffCent());
        assertEquals("SN001", saved.getSerialNo());
        assertEquals(PaymentRecord.STATUS_PENDING, saved.getStatus());
        assertEquals(NOW.toEpochMilli(), saved.getCreatedAtMillis());
        assertEquals("PR20240101000001", r.paymentNo());
        assertEquals(10L, r.customerId());
        assertEquals(50000L, r.amountCent());
        assertEquals(PaymentRecord.STATUS_PENDING, r.status());
    }

    @Test
    void amountMustBePositive() {
        BizException e = assertThrows(BizException.class,
                () -> useCase.record(new PaymentRecordCmd(10L, 0L, "SN002", 7L, "x")));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(paymentRecordRepository, never()).save(any());
    }

    @Test
    void customerMustBePositive() {
        BizException e = assertThrows(BizException.class,
                () -> useCase.record(new PaymentRecordCmd(0L, 50000L, "SN003", 7L, "x")));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(paymentRecordRepository, never()).save(any());
    }

    @Test
    void duplicateSerialRejected() {
        when(paymentRecordRepository.existsBySerialNo("SN004")).thenReturn(true);
        BizException e = assertThrows(BizException.class,
                () -> useCase.record(new PaymentRecordCmd(10L, 50000L, "SN004", 7L, "x")));
        assertEquals(ErrorCode.BIZ_REJECT, e.getErrorCode());
        verify(paymentRecordRepository, never()).save(any());
    }
}
