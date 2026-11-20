package com.sent.sports.payment;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.order.service.OrderPaymentService;
import com.sent.sports.order.service.dto.OrderPaymentView;
import com.sent.sports.payment.domain.PaymentTxn;
import com.sent.sports.payment.domain.PaymentTxnStatus;
import com.sent.sports.payment.gateway.PayGateway;
import com.sent.sports.payment.repository.PaymentRepository;
import com.sent.sports.payment.service.PayCallbackUseCase;
import com.sent.sports.payment.service.dto.PayCallbackCmd;
import com.sent.sports.payment.service.dto.PayCallbackResult;
import com.sent.sports.refund.service.RefundService;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付回调用例测试（P3-12，TDD 红）：依赖 Mock 的门面 / 仓储 / 通道，验证 plan §7.3 七步与验收 A2/A14
 * （验签拦截、幂等只入账一次、金额不符拒绝、已取消订单生成退款单且不复活、已支付幂等）。
 */
class PayCallbackUseCaseTest extends BaseUseCaseTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private OrderPaymentService orderPaymentService;
    @Mock
    private RefundService refundService;
    @Mock
    private PayGateway payGateway;
    @Mock
    private com.sent.sports.member.service.PointFacade pointFacade;
    @Mock
    private java.time.Clock clock;

    @InjectMocks
    private PayCallbackUseCase useCase;

    @Captor
    private ArgumentCaptor<PaymentTxn> txnCaptor;

    private static final Instant FIXED = Instant.parse("2024-01-01T00:00:00Z");

    @BeforeEach
    void signaturePassesByDefault() {
        // 除「伪造签名」用例外，其余用例都假定通道签名校验通过
        lenient().when(payGateway.verify(any())).thenReturn(true);
        lenient().when(clock.instant()).thenReturn(FIXED);
    }

    private Map<String, String> notifyParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("txnNo", "PR20240101000001");
        params.put("orderNo", "SO20240101000099");
        params.put("amountCent", "24200");
        params.put("channel", "WECHAT");
        params.put("payChannelTradeNo", "CHN123");
        params.put("paidAtMillis", "1704067200000");
        params.put("sign", "0123456789ABCDEF");
        return params;
    }

    private PayCallbackCmd cmd() {
        return PayCallbackCmd.fromNotify(notifyParams());
    }

    @Test
    @DisplayName("伪造签名：直接拒绝，不读订单、不落流水、不改状态")
    void forgedSignatureIsRejectedBeforeAnyBusinessLogic() {
        when(payGateway.verify(any())).thenReturn(false);

        BizException e = assertThrows(BizException.class, () -> useCase.handle(cmd()));

        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(orderPaymentService, never()).getView(anyString());
        verify(orderPaymentService, never()).markPaid(anyString(), anyLong(), anyLong());
        verify(paymentRepository, never()).save(any());
        verify(refundService, never()).createOnlyRefund(anyString(), anyLong(), anyString());
    }

    @Test
    @DisplayName("篡改金额：验签读到的是原始参数表，不是 DTO 解析出的业务字段")
    void tamperedAmountFailsVerification() {
        // 通道签的是 24200，报文里却带着 100：验签用原始参数表，篡改无处可藏
        Map<String, String> tampered = notifyParams();
        tampered.put("amountCent", "100");
        when(payGateway.verify(any())).thenAnswer(invocation -> {
            Map<String, String> params = invocation.getArgument(0);
            return "24200".equals(params.get("amountCent"));
        });

        assertThrows(BizException.class, () -> useCase.handle(PayCallbackCmd.fromNotify(tampered)));
        verify(orderPaymentService, never()).markPaid(anyString(), anyLong(), anyLong());
        verify(paymentRepository, never()).save(any());
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

    @Test
    @DisplayName("缺签名（空参数表）一律拒绝")
    void emptyParamsAreRejected() {
        when(payGateway.verify(any())).thenReturn(false);

        BizException e = assertThrows(BizException.class,
                () -> useCase.handle(new PayCallbackCmd("T1", "O1", 100L, "MOCK", "CH1", 0L)));

        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(orderPaymentService, never()).getView(anyString());
    }

    @Test
    @DisplayName("验签读取的是原始参数表，不是 DTO 里解析出来的业务字段")
    void verificationUsesRawNotifyParams() {        when(paymentRepository.findByTxnNo(anyString())).thenReturn(Optional.empty());
        when(orderPaymentService.getView(anyString()))
                .thenReturn(Optional.of(new OrderPaymentView("SO20240101000099", "WAIT_PAY", "UNPAID", 24200L)));

        useCase.handle(cmd());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.forClass(Map.class);
        verify(payGateway).verify(params.capture());
        assertEquals(List.of("txnNo", "orderNo", "amountCent", "channel", "payChannelTradeNo",
                        "paidAtMillis", "sign"),
                List.copyOf(params.getValue().keySet()));
    }

    // ==================== 支付成功扣减积分（P5 闭环） ====================

    @Test
    @DisplayName("待付款订单支付成功：按订单实际抵扣额扣减客户积分")
    void paidCallbackDeductsOrderPoints() {
        when(paymentRepository.findByTxnNo("PR20240101000001")).thenReturn(Optional.empty());
        when(orderPaymentService.getView("SO20240101000099"))
                .thenReturn(Optional.of(new OrderPaymentView("SO20240101000099", "WAIT_PAY", "UNPAID",
                        24200L, 9002L, 3000L)));

        PayCallbackResult result = useCase.handle(cmd());

        assertEquals("SUCCESS", result.status());
        // 3000 分抵扣 → 扣 3000 积分，来源单号必须是订单号
        verify(pointFacade).deductPoints(9002L, "SO20240101000099", 3000L);
    }

    @Test
    @DisplayName("本单没用积分：照常把 0 交给门面（门面内部直接返回、不写空流水），支付不受影响")
    void noPointDeductionStillPays() {
        when(paymentRepository.findByTxnNo("PR20240101000001")).thenReturn(Optional.empty());
        when(orderPaymentService.getView("SO20240101000099"))
                .thenReturn(Optional.of(new OrderPaymentView("SO20240101000099", "WAIT_PAY", "UNPAID",
                        24200L, 9002L, 0L)));

        assertEquals("SUCCESS", useCase.handle(cmd()).status());
        // 「0 就不落账」这条规则只在门面里判一次，用例不再复制一遍
        verify(pointFacade).deductPoints(9002L, "SO20240101000099", 0L);
    }

    @Test
    @DisplayName("扣积分失败（余额被并发花掉）：异常上抛，流水不落 PAID，回调按失败处理")
    void deductFailureBlocksCallback() {
        when(paymentRepository.findByTxnNo("PR20240101000001")).thenReturn(Optional.empty());
        when(orderPaymentService.getView("SO20240101000099"))
                .thenReturn(Optional.of(new OrderPaymentView("SO20240101000099", "WAIT_PAY", "UNPAID",
                        24200L, 9002L, 3000L)));
        when(pointFacade.deductPoints(9002L, "SO20240101000099", 3000L))
                .thenThrow(new BizException(ErrorCode.POINT_NOT_ENOUGH, "积分余额不足"));

        BizException e = assertThrows(BizException.class, () -> useCase.handle(cmd()));

        assertEquals(ErrorCode.POINT_NOT_ENOUGH, e.getErrorCode());
        // 关键：不能把支付流水写成 PAID —— 否则钱到账了、积分却没扣，渠道重试也不会再扣
        verify(paymentRepository, never()).save(any());
    }
}
