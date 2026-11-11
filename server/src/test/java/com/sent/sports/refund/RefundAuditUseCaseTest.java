package com.sent.sports.refund;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.refund.domain.RefundOrder;
import com.sent.sports.refund.domain.RefundStatus;
import com.sent.sports.refund.repository.RefundRepository;
import com.sent.sports.refund.service.RefundAuditUseCase;
import com.sent.sports.refund.service.RefundCallbackService;
import com.sent.sports.refund.service.dto.AuditRefundCmd;
import com.sent.sports.refund.service.dto.AuditRefundResult;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款审核用例测试（P3-20，TDD 红）：覆盖验收项——通过触发回调、驳回必填原因、二次申诉限一次。
 * 此时 RefundAuditUseCase 等尚未实现，编译即失败（红）。
 */
class RefundAuditUseCaseTest extends BaseUseCaseTest {

    @Mock
    private RefundRepository refundRepository;
    @Mock
    private RefundCallbackService refundCallbackService;

    @InjectMocks
    private RefundAuditUseCase useCase;

    @Captor
    private ArgumentCaptor<RefundOrder> saveCaptor;

    private RefundOrder pending() {
        return new RefundOrder("RF20240101000001", "SO20240101000099", 1L, 5000L, "REFUND", "reason",
                RefundStatus.PENDING, 0L);
    }

    @Test
    void approveTriggersCallbackAndSetsRefunding() {
        RefundOrder o = pending();
        when(refundRepository.findByRefundNo("RF20240101000001")).thenReturn(Optional.of(o));

        AuditRefundResult result = useCase.audit(new AuditRefundCmd("RF20240101000001", "APPROVE", null));

        assertEquals("REFUNDING", result.status());
        verify(refundCallbackService).process(any(RefundOrder.class));
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.REFUNDING, saveCaptor.getValue().getStatus());
    }

    @Test
    void approveOnlyFromPendingRejected() {
        RefundOrder o = pending();
        o.setStatus(RefundStatus.REFUNDING);
        when(refundRepository.findByRefundNo("RF20240101000001")).thenReturn(Optional.of(o));

        BizException ex = assertThrows(BizException.class,
                () -> useCase.audit(new AuditRefundCmd("RF20240101000001", "APPROVE", null)));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(refundCallbackService, never()).process(any());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void rejectRequiresReason() {
        RefundOrder o = pending();
        when(refundRepository.findByRefundNo("RF20240101000001")).thenReturn(Optional.of(o));

        BizException ex = assertThrows(BizException.class,
                () -> useCase.audit(new AuditRefundCmd("RF20240101000001", "REJECT", "  ")));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(refundRepository, never()).save(any());
        verify(refundCallbackService, never()).process(any());
    }

    @Test
    void rejectSetsClosed() {
        RefundOrder o = pending();
        when(refundRepository.findByRefundNo("RF20240101000001")).thenReturn(Optional.of(o));

        AuditRefundResult result = useCase.audit(new AuditRefundCmd("RF20240101000001", "REJECT", "不符合政策"));

        assertEquals("CLOSED", result.status());
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.CLOSED, saveCaptor.getValue().getStatus());
        verify(refundCallbackService, never()).process(any());
    }

    @Test
    void appealAfterRejectAllowed() {
        RefundOrder o = pending();
        o.setStatus(RefundStatus.CLOSED);
        when(refundRepository.findByRefundNo("RF20240101000001")).thenReturn(Optional.of(o));

        AuditRefundResult result = useCase.audit(new AuditRefundCmd("RF20240101000001", "APPEAL", null));

        assertEquals("PENDING", result.status());
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.PENDING, saveCaptor.getValue().getStatus());
        assertEquals(1, saveCaptor.getValue().getAppealCount());
    }

    @Test
    void secondAppealBlocked() {
        RefundOrder o = pending();
        o.setStatus(RefundStatus.CLOSED);
        o.setAppealCount(1);
        when(refundRepository.findByRefundNo("RF20240101000001")).thenReturn(Optional.of(o));

        BizException ex = assertThrows(BizException.class,
                () -> useCase.audit(new AuditRefundCmd("RF20240101000001", "APPEAL", null)));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(refundRepository, never()).save(any());
    }
}
