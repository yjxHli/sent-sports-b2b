package com.sent.sports.refund;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.notify.service.dto.InboxMessage;
import com.sent.sports.order.service.OrderRefundViewService;
import com.sent.sports.order.service.dto.OrderRefundView;
import com.sent.sports.refund.domain.RefundAction;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    @Mock
    private OrderRefundViewService orderRefundViewService;
    @Mock
    private MessageSendService messageSendService;

    @InjectMocks
    private RefundAuditUseCase useCase;

    @Captor
    private ArgumentCaptor<RefundOrder> saveCaptor;
    @Captor
    private ArgumentCaptor<InboxMessage> messageCaptor;

    private static final long CUSTOMER_ID = 9002L;

    private RefundOrder pending() {
        return new RefundOrder("RF20240101000001", "SO20240101000099", 1L, 5000L, "REFUND", "reason",
                RefundStatus.PENDING, 0L);
    }

    /** P2 批次：RefundOrder.setStatus 已删除，测试改用构造器直接落到目标状态。 */
    private RefundOrder inStatus(RefundStatus status) {
        return new RefundOrder("RF20240101000001", "SO20240101000099", 1L, 5000L, "REFUND", "reason",
                status, 0L);
    }

    private void givenOrderOwner() {
        when(orderRefundViewService.getView("SO20240101000099"))
                .thenReturn(Optional.of(new OrderRefundView("SO20240101000099", CUSTOMER_ID, 1L,
                        List.of(), 0L, false)));
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
        RefundOrder o = inStatus(RefundStatus.REFUNDING);
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
        givenOrderOwner();

        AuditRefundResult result = useCase.audit(new AuditRefundCmd("RF20240101000001", "REJECT", "不符合政策"));

        assertEquals("CLOSED", result.status());
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.CLOSED, saveCaptor.getValue().getStatus());
        verify(refundCallbackService, never()).process(any());
    }

    @Test
    void rejectSendsInboxNoticeToCustomer() {
        RefundOrder o = pending();
        when(refundRepository.findByRefundNo("RF20240101000001")).thenReturn(Optional.of(o));
        givenOrderOwner();

        useCase.audit(new AuditRefundCmd("RF20240101000001", "REJECT", "商品已使用，不符合退货条件"));

        verify(messageSendService).send(messageCaptor.capture());
        InboxMessage msg = messageCaptor.getValue();
        assertEquals(CUSTOMER_ID, msg.customerId());
        assertEquals("ORDER", msg.msgType());
        assertTrue(msg.title().contains("驳回"), "标题应点明退款被驳回，实际：" + msg.title());
        assertTrue(msg.content().contains("商品已使用，不符合退货条件"),
                "正文应带上驳回原因，实际：" + msg.content());
        assertEquals("REFUND", msg.bizType());
        assertEquals("RF20240101000001", msg.bizNo());
    }

    @Test
    void rejectKeepsAuditRemarkOnAggregate() {
        // 驳回原因要落 audit_remark，管理端列表的 reason 列才能显示「为什么被驳回」
        RefundOrder o = pending();
        when(refundRepository.findByRefundNo("RF20240101000001")).thenReturn(Optional.of(o));
        givenOrderOwner();

        useCase.audit(new AuditRefundCmd("RF20240101000001", "REJECT", "  凭证不足  "));

        verify(refundRepository).save(saveCaptor.capture());
        assertEquals("凭证不足", saveCaptor.getValue().getAuditRemark());
    }

    @Test
    void approveDoesNotSendRejectNotice() {
        RefundOrder o = pending();
        when(refundRepository.findByRefundNo("RF20240101000001")).thenReturn(Optional.of(o));

        useCase.audit(new AuditRefundCmd("RF20240101000001", "APPROVE", null));

        verify(messageSendService, never()).send(any());
    }

    @Test
    void rejectStillClosesWhenOwnerLookupFails() {
        // 订单已被物理清理时不能让审核动作整体失败：站内信发不出去，但退款单必须照样关闭
        RefundOrder o = pending();
        when(refundRepository.findByRefundNo("RF20240101000001")).thenReturn(Optional.of(o));
        when(orderRefundViewService.getView(anyString())).thenReturn(Optional.empty());

        AuditRefundResult result = useCase.audit(new AuditRefundCmd("RF20240101000001", "REJECT", "超时未寄回"));

        assertEquals("CLOSED", result.status());
        verify(messageSendService, never()).send(any());
    }

    @Test
    void appealAfterRejectAllowed() {
        RefundOrder o = inStatus(RefundStatus.CLOSED);
        when(refundRepository.findByRefundNo("RF20240101000001")).thenReturn(Optional.of(o));

        AuditRefundResult result = useCase.audit(new AuditRefundCmd("RF20240101000001", "APPEAL", null));

        assertEquals("PENDING", result.status());
        verify(refundRepository).save(saveCaptor.capture());
        assertEquals(RefundStatus.PENDING, saveCaptor.getValue().getStatus());
        assertEquals(1, saveCaptor.getValue().getAppealCount());
    }

    @Test
    void illegalTransitionRejectedByStateMachine() {
        // 退款中不允许再次 APPROVE / REJECT：错误状态变更统一 40901
        RefundOrder o = pending();
        o.transit(RefundAction.APPROVE);
        when(refundRepository.findByRefundNo("RF20240101000001")).thenReturn(Optional.of(o));

        BizException ex = assertThrows(BizException.class,
                () -> useCase.audit(new AuditRefundCmd("RF20240101000001", "APPROVE", null)));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(refundCallbackService, never()).process(any());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void unknownActionRejected() {
        RefundOrder o = pending();
        when(refundRepository.findByRefundNo("RF20240101000001")).thenReturn(Optional.of(o));

        BizException ex = assertThrows(BizException.class,
                () -> useCase.audit(new AuditRefundCmd("RF20240101000001", "NO_SUCH_ACTION", null)));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void secondAppealBlocked() {
        RefundOrder o = inStatus(RefundStatus.CLOSED);
        o.setAppealCount(1);
        when(refundRepository.findByRefundNo("RF20240101000001")).thenReturn(Optional.of(o));

        BizException ex = assertThrows(BizException.class,
                () -> useCase.audit(new AuditRefundCmd("RF20240101000001", "APPEAL", null)));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(refundRepository, never()).save(any());
    }
}
