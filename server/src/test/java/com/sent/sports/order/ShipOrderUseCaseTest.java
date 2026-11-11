package com.sent.sports.order;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.event.DomainEventPublisher;
import com.sent.sports.customize.service.CustomizeShipGateway;
import com.sent.sports.order.domain.Order;
import com.sent.sports.order.domain.OrderItem;
import com.sent.sports.order.domain.OrderStatus;
import com.sent.sports.order.event.ShipEvent;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.service.ShipOrderUseCase;
import com.sent.sports.order.service.ShipmentRecorder;
import com.sent.sports.order.service.dto.ShipLineCmd;
import com.sent.sports.order.service.dto.ShipOrderCmd;
import com.sent.sports.order.service.dto.ShipOrderResult;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 发货用例测试（P3-14，TDD 红）：依赖 Mock 的门面 / 仓储 / 事件发布器，
 * 覆盖 plan §7.4 与验收项：按行数量校验、定制未完工拒绝、超发拒绝、状态重算。
 * 此时 ShipOrderUseCase 等尚未实现，编译即失败（红）。
 */
class ShipOrderUseCaseTest extends BaseUseCaseTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomizeShipGateway customizeShipGateway;
    @Mock
    private DomainEventPublisher eventPublisher;
    @Mock
    private ShipmentRecorder shipmentRecorder;
    @Mock
    private java.time.Clock clock;

    @InjectMocks
    private ShipOrderUseCase useCase;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;
    @Captor
    private ArgumentCaptor<ShipEvent> eventCaptor;

    private static final Instant FIXED = Instant.parse("2024-01-01T00:00:00Z");

    private Order waitShipOrder(OrderItem... items) {
        Order order = new Order("SO20240101000099", 1L, "idem-1", List.of(items),
                10000L, 0L, 0L, 0L, 0L, 10000L, 0L);
        order.setStatus(OrderStatus.WAIT_SHIP);
        return order;
    }

    @Test
    void fullShipTransitionsToWaitReceiveAndPublishesShipEvent() {
        Order order = waitShipOrder(new OrderItem(1L, 5));
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));
        when(customizeShipGateway.isReadyToShip("SO20240101000099", 1L)).thenReturn(true);
        when(clock.instant()).thenReturn(FIXED);

        ShipOrderResult result = useCase.ship(
                new ShipOrderCmd("SO20240101000099", "SF", "SF123456", List.of(new ShipLineCmd(1L, 5))));

        assertEquals("WAIT_RECEIVE", result.status());
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.WAIT_RECEIVE, orderCaptor.getValue().getStatus());
        verify(eventPublisher).publish(eventCaptor.capture());
        assertEquals("SO20240101000099", eventCaptor.getValue().orderNo());

        // 发货落账：定制单门面 + 包裹记录必须在同一次成功发货里被调用
        verify(customizeShipGateway).createCustomOrdersForShipment("SO20240101000099");
        verify(shipmentRecorder).record(eq("SO20240101000099"), eq("SF"), eq("SF123456"), anyList());
    }

    @Test
    void partialShipKeepsPartShipped() {
        Order order = waitShipOrder(new OrderItem(1L, 5));
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));
        when(customizeShipGateway.isReadyToShip("SO20240101000099", 1L)).thenReturn(true);
        when(clock.instant()).thenReturn(FIXED);

        ShipOrderResult result = useCase.ship(
                new ShipOrderCmd("SO20240101000099", "SF", "SF123456", List.of(new ShipLineCmd(1L, 2))));

        assertEquals("PART_SHIPPED", result.status());
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.PART_SHIPPED, orderCaptor.getValue().getStatus());
        verify(eventPublisher).publish(any(ShipEvent.class));
    }

    @Test
    void overShipRejectedWithoutSideEffects() {
        Order order = waitShipOrder(new OrderItem(1L, 5));
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));
        when(customizeShipGateway.isReadyToShip("SO20240101000099", 1L)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> useCase.ship(
                new ShipOrderCmd("SO20240101000099", "SF", "SF123456", List.of(new ShipLineCmd(1L, 6)))));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void customizeNotFinishedRejected() {
        Order order = waitShipOrder(new OrderItem(1L, 5));
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));
        when(customizeShipGateway.isReadyToShip("SO20240101000099", 1L)).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () -> useCase.ship(
                new ShipOrderCmd("SO20240101000099", "SF", "SF123456", List.of(new ShipLineCmd(1L, 3)))));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void unknownSkuRejected() {
        Order order = waitShipOrder(new OrderItem(1L, 5));
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));

        BizException ex = assertThrows(BizException.class, () -> useCase.ship(
                new ShipOrderCmd("SO20240101000099", "SF", "SF123456", List.of(new ShipLineCmd(999L, 1)))));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void zeroQtyRejected() {
        Order order = waitShipOrder(new OrderItem(1L, 5));
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));
        when(customizeShipGateway.isReadyToShip("SO20240101000099", 1L)).thenReturn(true);

        BizException ex = assertThrows(BizException.class, () -> useCase.ship(
                new ShipOrderCmd("SO20240101000099", "SF", "SF123456", List.of(new ShipLineCmd(1L, 0)))));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
        verify(orderRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
}
