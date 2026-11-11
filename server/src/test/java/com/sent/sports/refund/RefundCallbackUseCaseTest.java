package com.sent.sports.refund;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.member.service.MemberRefundGateway;
import com.sent.sports.order.service.OrderRefundGateway;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.order.service.dto.OrderRefundView;
import com.sent.sports.payment.service.PaymentRefundGateway;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.refund.service.RefundCallbackUseCase;
import com.sent.sports.stock.service.StockRefundGateway;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款回调用例测试（P3-22，TDD 红）：覆盖 plan §7.6 与验收项——
 * 支付流水置 REFUNDED、退积分优先、释放额度/回补待检、全退关单、失败置 FAILED。
 * 此时 RefundCallbackUseCase 等尚未实现，编译即失败（红）。
 */
class RefundCallbackUseCaseTest extends BaseUseCaseTest {

    @Mock
    private RefundRepository refundRepository;
    @Mock
    private PaymentRefundGateway paymentRefundGateway;
    @Mock
    private OrderRefundViewService orderRefundViewService;
    @Mock
    private MemberRefundGateway memberRefundGateway;
    @Mock
    private StockRefundGateway stockRefundGateway;
    @Mock
    private OrderRefundGateway orderRefundGateway;

    @InjectMocks
    private RefundCallbackUseCase useCase;

    @Captor
    private ArgumentCaptor<RefundOrder> saveCaptor;

    private RefundOrder refunding() {
        return new RefundOrder("RF20240101000001", "SO20240101000099", 1L, 5000L, "REFUND", "reason",
                RefundStatus.REFUNDING, 0L);
    }

    private OrderRefundView view(long customerId) {
        return new OrderRefundView("SO20240101000099", customerId, 1L, List.of(), 0L, false);
    }

    @Test
    void successMarksRefundedAndTriggersDownstream() {
        RefundOrder o = refunding();
        when(orderRefundViewService.getView("SO20240101000099")).thenReturn(Optional.of(view(1L)));

        useCase.process(o);

        verify(paymentRefundGateway).markRefunded("SO20240101000099");
        verify(memberRefundGateway).refundPoints(1L, 5000L);
        verify(stockRefundGateway).releaseOnRefund("SO20240101000099");
        verify(orderRefundGateway).closeOnFullRefund("SO20240101000099");
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.REFUNDED, saveCaptor.getValue().getStatus());
    }

    @Test
    void paymentFailureSetsFailed() {
        RefundOrder o = refunding();
        doThrow(new BizException(ErrorCode.STATE_CONFLICT)).when(paymentRefundGateway).markRefunded("SO20240101000099");

        useCase.process(o);

        verify(memberRefundGateway, never()).refundPoints(anyLong(), anyLong());
        verify(stockRefundGateway, never()).releaseOnRefund(anyString());
        verify(orderRefundGateway, never()).closeOnFullRefund(anyString());
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.FAILED, saveCaptor.getValue().getStatus());
    }

    @Test
    void memberFailureSetsFailed() {
        RefundOrder o = refunding();
        when(orderRefundViewService.getView("SO20240101000099")).thenReturn(Optional.of(view(1L)));
        doThrow(new BizException(ErrorCode.STATE_CONFLICT)).when(memberRefundGateway).refundPoints(anyLong(), anyLong());

        useCase.process(o);

        verify(paymentRefundGateway).markRefunded("SO20240101000099");
        verify(stockRefundGateway, never()).releaseOnRefund(anyString());
        verify(orderRefundGateway, never()).closeOnFullRefund(anyString());
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.FAILED, saveCaptor.getValue().getStatus());
    }

    @Test
    void missingOrderViewSetsFailed() {
        RefundOrder o = refunding();
        when(orderRefundViewService.getView("SO20240101000099")).thenReturn(Optional.empty());

        useCase.process(o);

        verify(paymentRefundGateway).markRefunded("SO20240101000099");
        verify(memberRefundGateway, never()).refundPoints(anyLong(), anyLong());
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.FAILED, saveCaptor.getValue().getStatus());
    }
}
