package com.sent.sports.payment;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.order.service.OrderPaymentService;
import com.sent.sports.order.service.dto.OrderPaymentView;
import com.sent.sports.payment.domain.PaymentTxn;
import com.sent.sports.payment.domain.PaymentTxnStatus;
import com.sent.sports.payment.repository.PaymentRepository;
import com.sent.sports.payment.service.PayCallbackUseCase;
import com.sent.sports.payment.service.dto.PayCallbackCmd;
import com.sent.sports.payment.service.dto.PayCallbackResult;
import com.sent.sports.refund.service.RefundService;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付回调用例测试（P3-12，TDD 红）：依赖 Mock 的门面 / 仓储，验证 plan §7.3 七步与验收 A2/A14
 * （幂等只入账一次、金额不符拒绝、已取消订单生成退款单且不复活、已支付幂等）。
 * 此时 PayCallbackUseCase 尚未实现，编译即失败（红）。
 */
class PayCallbackUseCaseTest extends BaseUseCaseTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderPaymentService orderPaymentService;
    @Mock
    private RefundService refundService;
    @Mock
    private java.time.Clock clock;

    @InjectMocks
    private PayCallbackUseCase useCase;

    @Captor
    private ArgumentCaptor<PaymentTxn> txnCaptor;

    private static final Instant FIXED = Instant.parse("2024-01-01T00:00:00Z");

    private PayCallbackCmd cmd() {
        return new PayCallbackCmd("PR20240101000001", "SO20240101000099", 24200L, "WECHAT", "CHN123", 1704067200000L);
    }

    @Test
    void normalPaidTransitionsOrderAndMarksTxnPaid() {
        when(clock.instant()).thenReturn(FIXED);
        when(paymentRepository.findByTxnNo("PR20240101000001")).thenReturn(Optional.empty());
        when(orderPaymentService.getView("SO20240101000099"))
                .thenReturn(Optional.of(new OrderPaymentView("SO20240101000099", "WAIT_PAY", "UNPAID", 24200L)));

        PayCallbackResult result = useCase.handle(cmd());

        assertEquals("SUCCESS", result.status());
        verify(orderPaymentService).markPaid("SO20240101000099", 24200L, 1704067200000L);
        verify(paymentRepository).save(txnCaptor.capture());
        assertEquals(PaymentTxnStatus.PAID, txnCaptor.getValue().getStatus());
        verify(refundService, never()).createOnlyRefund(anyString(), anyLong(), anyString());
    }

    @Test
    void createdTxnFromPaymentCreationDoesNotBlockCallback() {
        PaymentTxn created = new PaymentTxn("PR20240101000001", "SO20240101000099", 24200L,
                "WECHAT", "PAY-IDEM-1", 1704067200000L);
        when(paymentRepository.findByTxnNo("PR20240101000001")).thenReturn(Optional.of(created));
        when(orderPaymentService.getView("SO20240101000099"))
                .thenReturn(Optional.of(new OrderPaymentView("SO20240101000099", "WAIT_PAY", "UNPAID", 24200L)));

        PayCallbackResult result = useCase.handle(cmd());

        assertEquals("SUCCESS", result.status());
        verify(orderPaymentService).markPaid("SO20240101000099", 24200L, 1704067200000L);
        verify(paymentRepository).save(txnCaptor.capture());
        assertEquals(PaymentTxnStatus.PAID, txnCaptor.getValue().getStatus());
        assertEquals("PAY-IDEM-1", txnCaptor.getValue().getIdemKey(), "复用原流水不得丢幂等键");
    }

    @Test
    void duplicateCallbackReturnsAlreadyPaidWithoutReprocessing() {
        PaymentTxn paid = new PaymentTxn("PR20240101000001", "SO20240101000099", 24200L, "WECHAT", "PR20240101000001", 1704067200000L);
        paid.markPaid("CHN123");
        when(paymentRepository.findByTxnNo("PR20240101000001")).thenReturn(Optional.of(paid));

        PayCallbackResult result = useCase.handle(cmd());

        assertEquals("ALREADY_PAID", result.status());
        verify(orderPaymentService, never()).markPaid(anyString(), anyLong(), anyLong());
        verify(refundService, never()).createOnlyRefund(anyString(), anyLong(), anyString());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void amountMismatchRejected() {
        when(paymentRepository.findByTxnNo("PR20240101000001")).thenReturn(Optional.empty());
        when(orderPaymentService.getView("SO20240101000099"))
                .thenReturn(Optional.of(new OrderPaymentView("SO20240101000099", "WAIT_PAY", "UNPAID", 100L)));

        PayCallbackResult result = useCase.handle(cmd());

        assertEquals("MISMATCH", result.status());
        verify(orderPaymentService, never()).markPaid(anyString(), anyLong(), anyLong());
        verify(refundService, never()).createOnlyRefund(anyString(), anyLong(), anyString());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void lateCallbackOnCancelledOrderCreatesRefundAndKeepsOrderCancelled() {
        when(clock.instant()).thenReturn(FIXED);
        when(paymentRepository.findByTxnNo("PR20240101000001")).thenReturn(Optional.empty());
        when(orderPaymentService.getView("SO20240101000099"))
                .thenReturn(Optional.of(new OrderPaymentView("SO20240101000099", "CANCELLED", "UNPAID", 24200L)));

        PayCallbackResult result = useCase.handle(cmd());

        assertEquals("REFUNDED", result.status());
        verify(refundService).createOnlyRefund("SO20240101000099", 24200L, "PAID_AFTER_CANCEL");
        verify(orderPaymentService, never()).markPaid(anyString(), anyLong(), anyLong());
        verify(paymentRepository).save(txnCaptor.capture());
        assertEquals(PaymentTxnStatus.PAID, txnCaptor.getValue().getStatus());
    }

    @Test
    void alreadyPaidOrderIdempotent() {
        when(clock.instant()).thenReturn(FIXED);
        when(paymentRepository.findByTxnNo("PR20240101000001")).thenReturn(Optional.empty());
        when(orderPaymentService.getView("SO20240101000099"))
                .thenReturn(Optional.of(new OrderPaymentView("SO20240101000099", "WAIT_SHIP", "PAID", 24200L)));

        PayCallbackResult result = useCase.handle(cmd());

        assertEquals("ALREADY_PAID", result.status());
        verify(orderPaymentService, never()).markPaid(anyString(), anyLong(), anyLong());
        verify(refundService, never()).createOnlyRefund(anyString(), anyLong(), anyString());
        verify(paymentRepository).save(txnCaptor.capture());
        assertEquals(PaymentTxnStatus.PAID, txnCaptor.getValue().getStatus());
    }
}
