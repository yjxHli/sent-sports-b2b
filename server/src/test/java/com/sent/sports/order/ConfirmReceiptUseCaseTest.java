package com.sent.sports.order;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.service.OrderSettlementService;
import com.sent.sports.member.service.MemberRewardService;
import com.sent.sports.order.domain.Order;
import com.sent.sports.order.domain.OrderItem;
import com.sent.sports.order.domain.OrderStatus;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.service.ConfirmReceiptUseCase;
import com.sent.sports.order.service.dto.ConfirmReceiptCmd;
import com.sent.sports.order.service.dto.ConfirmReceiptResult;
import com.sent.sports.order.service.dto.ReceiveLineCmd;
import com.sent.sports.support.BaseUseCaseTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 确认收货用例测试（P3-16）：覆盖 plan 验收项——逐包裹确认、全部确认才完成、完成后发成长值与积分。
 */
class ConfirmReceiptUseCaseTest extends BaseUseCaseTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private MemberRewardService memberRewardService;
    @Mock
    private OrderSettlementService orderSettlementService;

    @InjectMocks
    private ConfirmReceiptUseCase useCase;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    private Order orderIn(OrderStatus status, OrderItem... items) {
        return new Order("SO20240101000099", 1L, "idem-1", List.of(items),
                10000L, 0L, 0L, 0L, 0L, 10000L, 0L, status);
    }

    private Order waitReceiveOrder(OrderItem... items) {
        return orderIn(OrderStatus.WAIT_RECEIVE, items);
    }

    @Test
    void fullReceiptCompletesOrderAndGrantsReward() {
        Order order = waitReceiveOrder(new OrderItem(1L, 5));
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));

        ConfirmReceiptResult result = useCase.confirm(
                new ConfirmReceiptCmd("SO20240101000099", List.of(new ReceiveLineCmd(1L, 5))));

        assertEquals("COMPLETED", result.status());
        assertTrue(result.completed());
        verify(memberRewardService).grantOrderCompleted(eq(1L), eq("SO20240101000099"), eq(10000L));
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.COMPLETED, orderCaptor.getValue().getStatus());
    }

    @Test
    void emptyLinesConfirmsAllShippedItems() {
        // 用户端「确认收货」按钮不带明细（列表页只拿得到订单号）：按已发未收数量全量确认
        OrderItem shipped = new OrderItem(1L, 5);
        shipped.ship(5);
        Order order = waitReceiveOrder(shipped);
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));

        ConfirmReceiptResult result = useCase.confirm(
                new ConfirmReceiptCmd("SO20240101000099", List.of()));

        assertEquals("COMPLETED", result.status());
        assertTrue(result.completed());
        verify(memberRewardService).grantOrderCompleted(eq(1L), eq("SO20240101000099"), eq(10000L));
        verify(orderRepository).save(any());
    }

    @Test
    void partialReceiptKeepsWaitReceiveAndNoReward() {
        Order order = waitReceiveOrder(new OrderItem(1L, 5));
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));

        ConfirmReceiptResult result = useCase.confirm(
                new ConfirmReceiptCmd("SO20240101000099", List.of(new ReceiveLineCmd(1L, 2))));

        assertEquals("WAIT_RECEIVE", result.status());
        assertFalse(result.completed());
        verify(memberRewardService, never()).grantOrderCompleted(anyLong(), anyString(), anyLong());
        verify(orderRepository).save(any());
    }

    @Test
    void overReceiveRejectedWithoutSideEffects() {
        Order order = waitReceiveOrder(new OrderItem(1L, 5));
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));

        BizException ex = assertThrows(BizException.class, () -> useCase.confirm(
                new ConfirmReceiptCmd("SO20240101000099", List.of(new ReceiveLineCmd(1L, 6)))));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(orderRepository, never()).save(any());
        verify(memberRewardService, never()).grantOrderCompleted(anyLong(), anyString(), anyLong());
    }

    @Test
    void unknownSkuRejected() {
        Order order = waitReceiveOrder(new OrderItem(1L, 5));
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));

        BizException ex = assertThrows(BizException.class, () -> useCase.confirm(
                new ConfirmReceiptCmd("SO20240101000099", List.of(new ReceiveLineCmd(999L, 1)))));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(orderRepository, never()).save(any());
        verify(memberRewardService, never()).grantOrderCompleted(anyLong(), anyString(), anyLong());
    }

    @Test
    void notReceivableStateRejected() {
        Order order = orderIn(OrderStatus.COMPLETED, new OrderItem(1L, 5));
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));

        BizException ex = assertThrows(BizException.class, () -> useCase.confirm(
                new ConfirmReceiptCmd("SO20240101000099", List.of(new ReceiveLineCmd(1L, 5)))));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(orderRepository, never()).save(any());
        verify(memberRewardService, never()).grantOrderCompleted(anyLong(), anyString(), anyLong());
    }

    @Test
    void partShippedOrderCanConfirmReceive() {
        // 状态机允许 PART_SHIPPED --CONFIRM_RECEIVE--> COMPLETED；用例白名单曾与此冲突
        OrderItem shipped = new OrderItem(1L, 5);
        shipped.ship(5);
        Order order = orderIn(OrderStatus.PART_SHIPPED, shipped);
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));

        ConfirmReceiptResult result = useCase.confirm(
                new ConfirmReceiptCmd("SO20240101000099", List.of(new ReceiveLineCmd(1L, 5))));

        assertEquals("COMPLETED", result.status());
        assertTrue(result.completed());
        verify(memberRewardService).grantOrderCompleted(eq(1L), eq("SO20240101000099"), eq(10000L));
        verify(orderRepository).save(any());
    }

    @Test
    void waitShipOrderCannotConfirmReceive() {
        // 准入由状态机裁决，而不是用例里另写一份列表
        Order order = orderIn(OrderStatus.WAIT_SHIP, new OrderItem(1L, 5));
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));

        BizException ex = assertThrows(BizException.class, () -> useCase.confirm(
                new ConfirmReceiptCmd("SO20240101000099", List.of())));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void zeroQtyRejected() {
        Order order = waitReceiveOrder(new OrderItem(1L, 5));
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));

        BizException ex = assertThrows(BizException.class, () -> useCase.confirm(
                new ConfirmReceiptCmd("SO20240101000099", List.of(new ReceiveLineCmd(1L, 0)))));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(orderRepository, never()).save(any());
        verify(memberRewardService, never()).grantOrderCompleted(anyLong(), anyString(), anyLong());
    }

    // ---------------- P0-02：确认收货归属校验 ----------------

    @Test
    void confirmRejectsOtherCustomer() {
        Order order = waitReceiveOrder(new OrderItem(1L, 5));
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));

        BizException ex = assertThrows(BizException.class, () -> useCase.confirm(
                new ConfirmReceiptCmd("SO20240101000099", List.of(new ReceiveLineCmd(1L, 5))), 999L));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(orderRepository, never()).save(any());
        verify(memberRewardService, never()).grantOrderCompleted(anyLong(), anyString(), anyLong());
        verify(orderSettlementService, never()).onOrderCompleted(anyString(), anyLong(), anyLong());
    }

    @Test
    void confirmAllowsOwner() {
        Order order = waitReceiveOrder(new OrderItem(1L, 5));
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));

        ConfirmReceiptResult result = useCase.confirm(
                new ConfirmReceiptCmd("SO20240101000099", List.of(new ReceiveLineCmd(1L, 5))), 1L);

        assertEquals("COMPLETED", result.status());
        verify(orderRepository).save(any());
    }
}
