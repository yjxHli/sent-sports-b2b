package com.sent.sports.refund;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.util.IdGen;
import com.sent.sports.customize.service.CustomizeRefundGateway;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.order.service.dto.OrderRefundView;
import com.sent.sports.order.service.dto.RefundLineView;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.refund.service.RefundApplyUseCase;
import com.sent.sports.refund.service.dto.RefundApplyCmd;
import com.sent.sports.refund.service.dto.RefundApplyLineCmd;
import com.sent.sports.refund.service.dto.RefundApplyResult;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 申请退款用例测试（P3-18，TDD 红）：覆盖 plan §7.6 与验收项——
 * 凭证必填、定制已排产不可退款、超退拒绝(REFUND_OVERFLOW)、未知行/订单不存在、生成待审核退款单。
 * 此时 RefundApplyUseCase 等尚未实现，编译即失败（红）。
 */
class RefundApplyUseCaseTest extends BaseUseCaseTest {

    @Mock
    private OrderRefundViewService orderRefundViewService;
    @Mock
    private CustomizeRefundGateway customizeRefundGateway;
    @Mock
    private RefundRepository refundRepository;
    @Mock
    private IdGen idGen;
    @Mock
    private java.time.Clock clock;

    @InjectMocks
    private RefundApplyUseCase useCase;

    @Captor
    private ArgumentCaptor<RefundOrder> refundCaptor;

    private static final Instant FIXED = Instant.parse("2024-01-01T00:00:00Z");

    private OrderRefundView singleLineView(long paid, long already, long shipping, boolean shipped) {
        return new OrderRefundView("SO20240101000099", 1L, 1L,
                List.of(new RefundLineView(1L, paid, already)), shipping, shipped);
    }

    @Test
    void applyCreatesPendingRefundOrderAndReturnsResult() {
        when(orderRefundViewService.getView("SO20240101000099"))
                .thenReturn(Optional.of(singleLineView(10000L, 0L, 0L, false)));
        when(customizeRefundGateway.isRefundBlocked("SO20240101000099", 1L)).thenReturn(false);
        when(idGen.next("RF")).thenReturn("RF20240101000001");
        when(clock.instant()).thenReturn(FIXED);

        RefundApplyResult result = useCase.apply(new RefundApplyCmd("SO20240101000099",
                List.of(new RefundApplyLineCmd(1L, 5000L)), "不想要了", "proof-img"));

        assertEquals("RF20240101000001", result.refundNo());
        assertEquals("SO20240101000099", result.orderNo());
        assertEquals("PENDING", result.status());
        assertEquals(5000L, result.amountCent());
        verify(refundRepository).save(refundCaptor.capture());
        RefundOrder saved = refundCaptor.getValue();
        assertEquals(RefundStatus.PENDING, saved.getStatus());
        assertEquals(5000L, saved.getAmountCent());
        assertEquals("REFUND", saved.getType());
    }

    @Test
    void overRefundRejected() {
        when(orderRefundViewService.getView("SO20240101000099"))
                .thenReturn(Optional.of(singleLineView(10000L, 0L, 0L, false)));
        when(customizeRefundGateway.isRefundBlocked(anyString(), anyLong())).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> useCase.apply(new RefundApplyCmd(
                "SO20240101000099", List.of(new RefundApplyLineCmd(1L, 15000L)), "r", "p")));
        assertEquals(ErrorCode.REFUND_OVERFLOW, ex.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void customizeInProductionRejected() {
        when(orderRefundViewService.getView("SO20240101000099"))
                .thenReturn(Optional.of(singleLineView(10000L, 0L, 0L, false)));
        when(customizeRefundGateway.isRefundBlocked("SO20240101000099", 1L)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> useCase.apply(new RefundApplyCmd(
                "SO20240101000099", List.of(new RefundApplyLineCmd(1L, 5000L)), "r", "p")));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void missingProofRejected() {
        BizException ex = assertThrows(BizException.class, () -> useCase.apply(new RefundApplyCmd(
                "SO20240101000099", List.of(new RefundApplyLineCmd(1L, 5000L)), "r", "  ")));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void unknownSkuRejected() {
        when(orderRefundViewService.getView("SO20240101000099"))
                .thenReturn(Optional.of(singleLineView(10000L, 0L, 0L, false)));
        when(customizeRefundGateway.isRefundBlocked(anyString(), anyLong())).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> useCase.apply(new RefundApplyCmd(
                "SO20240101000099", List.of(new RefundApplyLineCmd(999L, 5000L)), "r", "p")));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void orderNotFoundRejected() {
        when(orderRefundViewService.getView("SO20240101000099")).thenReturn(Optional.empty());

        BizException ex = assertThrows(BizException.class, () -> useCase.apply(new RefundApplyCmd(
                "SO20240101000099", List.of(new RefundApplyLineCmd(1L, 5000L)), "r", "p")));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(refundRepository, never()).save(any());
    }
}
