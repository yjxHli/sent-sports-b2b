package com.sent.sports.refund;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.coupon.service.CouponDeduction;
import com.sent.sports.member.service.MemberRefundGateway;
import com.sent.sports.order.service.OrderRefundGateway;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.order.service.dto.OrderRefundView;
import com.sent.sports.order.service.dto.RefundLineView;
import com.sent.sports.payment.service.PaymentRefundGateway;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.refund.service.RefundCallbackUseCase;
import com.sent.sports.refund.service.RefundSuccessNotifier;
import com.sent.sports.stock.service.StockRefundGateway;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.DisplayName;
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
    private CouponDeduction couponDeduction;
    @Mock
    private StockRefundGateway stockRefundGateway;
    @Mock
    private OrderRefundGateway orderRefundGateway;
    @Mock
    private RefundSuccessNotifier refundSuccessNotifier;

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
        verify(memberRefundGateway).refundPoints(1L, "RF20240101000001", 5000L);
        verify(stockRefundGateway).releaseOnRefund("SO20240101000099");
        verify(orderRefundGateway).closeOnFullRefund("SO20240101000099");
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.REFUNDED, saveCaptor.getValue().getStatus());
        verify(refundSuccessNotifier).notify(saveCaptor.getValue());
    }

    @Test
    void paymentFailureSetsFailed() {
        RefundOrder o = refunding();
        doThrow(new BizException(ErrorCode.STATE_CONFLICT)).when(paymentRefundGateway).markRefunded("SO20240101000099");

        useCase.process(o);

        verify(memberRefundGateway, never()).refundPoints(anyLong(), anyString(), anyLong());
        verify(stockRefundGateway, never()).releaseOnRefund(anyString());
        verify(orderRefundGateway, never()).closeOnFullRefund(anyString());
        verify(refundSuccessNotifier, never()).notify(org.mockito.ArgumentMatchers.any());
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.FAILED, saveCaptor.getValue().getStatus());
    }

    @Test
    void memberFailureSetsFailed() {
        RefundOrder o = refunding();
        when(orderRefundViewService.getView("SO20240101000099")).thenReturn(Optional.of(view(1L)));
        doThrow(new BizException(ErrorCode.STATE_CONFLICT)).when(memberRefundGateway).refundPoints(anyLong(), anyString(), anyLong());

        useCase.process(o);

        verify(paymentRefundGateway).markRefunded("SO20240101000099");
        verify(stockRefundGateway, never()).releaseOnRefund(anyString());
        verify(orderRefundGateway, never()).closeOnFullRefund(anyString());
        verify(refundSuccessNotifier, never()).notify(org.mockito.ArgumentMatchers.any());
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.FAILED, saveCaptor.getValue().getStatus());
    }

    @Test
    void missingOrderViewSetsFailed() {
        RefundOrder o = refunding();
        when(orderRefundViewService.getView("SO20240101000099")).thenReturn(Optional.empty());

        useCase.process(o);

        verify(paymentRefundGateway).markRefunded("SO20240101000099");
        verify(memberRefundGateway, never()).refundPoints(anyLong(), anyString(), anyLong());
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.FAILED, saveCaptor.getValue().getStatus());
    }

    // ==================== 退款退回抵扣积分（主流电商口径） ====================

    private OrderRefundView viewWithLines(long customerId, long linePaidCent, long payableCent) {
        return new OrderRefundView("SO20240101000099", customerId, 1L,
                List.of(new RefundLineView(9L, linePaidCent, 0L)), 0L, false, payableCent);
    }

    @Test
    @DisplayName("整单退款：把订单实际扣过的抵扣积分原额退回并落 refund_order.point_refund")
    void fullRefundReturnsDeductedPoints() {
        RefundOrder o = refunding();   // 退款额 5000 = 行实付 5000 → 整单
        when(orderRefundViewService.getView("SO20240101000099"))
                .thenReturn(Optional.of(viewWithLines(1L, 5000L, 7000L)));
        when(memberRefundGateway.deductedPointsOf(1L, "SO20240101000099")).thenReturn(3000L);

        useCase.process(o);

        verify(memberRefundGateway).returnDeductedPoints(1L, "RF20240101000001", 3000L);
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(3000L, saveCaptor.getValue().getPointRefund(), "退回积分数必须落库");
        assertEquals(RefundStatus.REFUNDED, saveCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("部分退款：按退款额占可退额的比例退回抵扣积分（向下取整）")
    void partialRefundReturnsProportionalPoints() {
        RefundOrder o = refunding();   // 退款额 5000，行实付 10000 → 一半
        when(orderRefundViewService.getView("SO20240101000099"))
                .thenReturn(Optional.of(viewWithLines(1L, 10_000L, 7000L)));
        when(memberRefundGateway.deductedPointsOf(1L, "SO20240101000099")).thenReturn(3000L);

        useCase.process(o);

        verify(memberRefundGateway).returnDeductedPoints(1L, "RF20240101000001", 1500L);
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(1500L, saveCaptor.getValue().getPointRefund());
    }

    @Test
    @DisplayName("订单没用积分抵扣（台账无扣减记录）：不退回、不写空流水")
    void noDeductionReturnsNothing() {
        RefundOrder o = refunding();
        when(orderRefundViewService.getView("SO20240101000099"))
                .thenReturn(Optional.of(viewWithLines(1L, 5000L, 7000L)));
        when(memberRefundGateway.deductedPointsOf(1L, "SO20240101000099")).thenReturn(0L);

        useCase.process(o);

        verify(memberRefundGateway, never()).returnDeductedPoints(anyLong(), anyString(), anyLong());
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(0L, saveCaptor.getValue().getPointRefund());
    }

    @Test
    @DisplayName("退回积分失败：置 FAILED 待人工对账（钱已退、积分没回）")
    void returnPointsFailureSetsFailed() {
        RefundOrder o = refunding();
        when(orderRefundViewService.getView("SO20240101000099"))
                .thenReturn(Optional.of(viewWithLines(1L, 5000L, 7000L)));
        when(memberRefundGateway.deductedPointsOf(1L, "SO20240101000099")).thenReturn(3000L);
        doThrow(new BizException(ErrorCode.SYSTEM_ERROR)).when(memberRefundGateway)
                .returnDeductedPoints(anyLong(), anyString(), anyLong());

        useCase.process(o);

        verify(stockRefundGateway, never()).releaseOnRefund(anyString());
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.FAILED, saveCaptor.getValue().getStatus());
    }

    // ==================== 整单退返还优惠券 ====================

    @Test
    @DisplayName("整单退款：下单时核销的优惠券退回账户（与积分同口径）")
    void fullRefundReturnsCoupon() {
        RefundOrder o = refunding();   // 5000 = 行实付 5000 → 整单
        when(orderRefundViewService.getView("SO20240101000099"))
                .thenReturn(Optional.of(viewWithLines(1L, 5000L, 7000L)));

        useCase.process(o);

        verify(couponDeduction).returnCoupon("SO20240101000099");
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.REFUNDED, saveCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("部分退款：不退券（否则同一张券能被用两次）")
    void partialRefundKeepsCoupon() {
        RefundOrder o = refunding();   // 5000，行实付 10000 → 只退一半
        when(orderRefundViewService.getView("SO20240101000099"))
                .thenReturn(Optional.of(viewWithLines(1L, 10_000L, 7000L)));

        useCase.process(o);

        verify(couponDeduction, never()).returnCoupon(anyString());
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.REFUNDED, saveCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("退券失败：置 FAILED 待人工对账（钱已退、券没回）")
    void couponReturnFailureSetsFailed() {
        RefundOrder o = refunding();
        when(orderRefundViewService.getView("SO20240101000099"))
                .thenReturn(Optional.of(viewWithLines(1L, 5000L, 7000L)));
        when(couponDeduction.returnCoupon(anyString())).thenThrow(new BizException(ErrorCode.SYSTEM_ERROR));

        useCase.process(o);

        verify(stockRefundGateway, never()).releaseOnRefund(anyString());
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.FAILED, saveCaptor.getValue().getStatus());
    }
}
