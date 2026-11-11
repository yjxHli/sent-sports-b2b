package com.sent.sports.payment;

import com.sent.sports.common.domain.IdemKey;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.order.service.OrderQueryService;
import com.sent.sports.order.service.dto.OrderPaymentStatusDto;
import com.sent.sports.payment.domain.PaymentTxn;
import com.sent.sports.payment.domain.PaymentTxnStatus;
import com.sent.sports.payment.repository.PaymentRepository;
import com.sent.sports.payment.service.CreatePaymentUseCase;
import com.sent.sports.payment.service.dto.CreatePaymentCmd;
import com.sent.sports.payment.service.dto.CreatePaymentResult;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发起支付用例测试（P3-10，TDD 红）：依赖 Mock 的门面 / 仓储，验证
 * 生成流水、幂等重放、已支付不可重复发起、幂等键必填、金额一致。
 * 此时 CreatePaymentUseCase 尚未实现，编译即失败（红）。
 */
class CreatePaymentUseCaseTest extends BaseUseCaseTest {

    @Mock
    private OrderQueryService orderQueryService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private java.time.Clock clock;

    @InjectMocks
    private CreatePaymentUseCase useCase;

    @Captor
    private ArgumentCaptor<PaymentTxn> txnCaptor;

    private static final Instant FIXED = Instant.parse("2024-01-01T00:00:00Z");

    @Test
    void createGeneratesPaymentTxn() {
        when(clock.instant()).thenReturn(FIXED);
        when(paymentRepository.findByIdemKey("idem-pay-1")).thenReturn(Optional.empty());
        when(orderQueryService.getPaymentStatus("SO20240101000099"))
                .thenReturn(Optional.of(new OrderPaymentStatusDto("SO20240101000099", true, 24200L)));
        when(idGen.next("PR")).thenReturn("PR20240101000001");

        CreatePaymentCmd cmd = new CreatePaymentCmd("SO20240101000099", 24200L, "WECHAT", "idem-pay-1");
        CreatePaymentResult result = useCase.create(cmd);

        assertEquals("PR20240101000001", result.txnNo());
        assertEquals("CREATED", result.status());
        assertEquals(24200L, result.amountCent());
        assertEquals("SO20240101000099", result.orderNo());

        verify(paymentRepository).save(txnCaptor.capture());
        PaymentTxn saved = txnCaptor.getValue();
        assertEquals("PR20240101000001", saved.getTxnNo());
        assertEquals(PaymentTxnStatus.CREATED, saved.getStatus());
        assertEquals("idem-pay-1", saved.getIdemKey());
        assertEquals(24200L, saved.getAmountCent());
    }

    @Test
    void idempotentReplayReturnsExisting() {
        PaymentTxn existing = new PaymentTxn("PR20240101000001", "SO20240101000099", 24200L, "WECHAT", "idem-pay-1", 1704067200000L);
        when(paymentRepository.findByIdemKey("idem-pay-1")).thenReturn(Optional.of(existing));

        CreatePaymentCmd cmd = new CreatePaymentCmd("SO20240101000099", 24200L, "WECHAT", "idem-pay-1");
        CreatePaymentResult result = useCase.create(cmd);

        assertEquals("PR20240101000001", result.txnNo());
        verify(paymentRepository, never()).save(any());
        verify(orderQueryService, never()).getPaymentStatus(any());
    }

    @Test
    void alreadyPaidOrderRejected() {
        when(paymentRepository.findByIdemKey("idem-pay-2")).thenReturn(Optional.empty());
        when(orderQueryService.getPaymentStatus("SO20240101000099"))
                .thenReturn(Optional.of(new OrderPaymentStatusDto("SO20240101000099", false, 24200L)));

        CreatePaymentCmd cmd = new CreatePaymentCmd("SO20240101000099", 24200L, "WECHAT", "idem-pay-2");
        BizException ex = assertThrows(BizException.class, () -> useCase.create(cmd));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void missingIdemKeyRejected() {
        CreatePaymentCmd cmd = new CreatePaymentCmd("SO20240101000099", 24200L, "WECHAT", null);
        BizException ex = assertThrows(BizException.class, () -> useCase.create(cmd));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
    }

    @Test
    void amountMismatchRejected() {
        when(paymentRepository.findByIdemKey("idem-pay-3")).thenReturn(Optional.empty());
        when(orderQueryService.getPaymentStatus("SO20240101000099"))
                .thenReturn(Optional.of(new OrderPaymentStatusDto("SO20240101000099", true, 24200L)));

        CreatePaymentCmd cmd = new CreatePaymentCmd("SO20240101000099", 100L, "WECHAT", "idem-pay-3");
        BizException ex = assertThrows(BizException.class, () -> useCase.create(cmd));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(paymentRepository, never()).save(any());
    }
}
