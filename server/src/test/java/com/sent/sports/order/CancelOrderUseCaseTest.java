package com.sent.sports.order;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.order.domain.Order;
import com.sent.sports.order.domain.OrderItem;
import com.sent.sports.order.domain.OrderStatus;
import com.sent.sports.order.repository.OrderRepository;
import com.sent.sports.order.service.CancelOrderUseCase;
import com.sent.sports.order.service.dto.CancelOrderCmd;
import com.sent.sports.order.service.dto.CancelOrderResult;
import com.sent.sports.stock.repository.StockRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 取消订单用例测试（P3-08，TDD 红）：依赖 Mock 仓储，验证仅待付款可取消、释放预占、
 * 已支付/已取消抛 STATE_CONFLICT(40901)、订单不存在抛 PARAM_INVALID。
 * 此时 CancelOrderUseCase 尚未实现，编译即失败（红）。
 */
class CancelOrderUseCaseTest extends BaseUseCaseTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private StockRepository stockRepository;

    @InjectMocks
    private CancelOrderUseCase useCase;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    private Order waitPayOrder() {
        Order order = new Order("SO20240101000099", 100L, "idem-1",
                List.of(new OrderItem(1L, 2, 9000L, "MEMBER", 18000L),
                        new OrderItem(2L, 1, 10000L, "RETAIL", 10000L)),
                28000L, 0L, 0L, 1200L, 5000L, 24200L, 1704067200000L);
        order.setStatus(OrderStatus.WAIT_PAY);
        return order;
    }

    @Test
    void cancelWaitPayReleasesStockAndMarksCancelled() {
        Order order = waitPayOrder();
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));

        CancelOrderResult result = useCase.cancel(new CancelOrderCmd("SO20240101000099"));

        assertEquals("CANCELLED", result.status());
        verify(stockRepository).release(1L, 2);
        verify(stockRepository).release(2L, 1);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(OrderStatus.CANCELLED, orderCaptor.getValue().getStatus());
    }

    @Test
    void cancelPaidOrderRejected() {
        Order order = waitPayOrder();
        order.setStatus(OrderStatus.WAIT_SHIP); // 已支付待发货
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));

        BizException ex = assertThrows(BizException.class,
                () -> useCase.cancel(new CancelOrderCmd("SO20240101000099")));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(stockRepository, never()).release(anyLong(), anyInt());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelAlreadyCancelledRejected() {
        Order order = waitPayOrder();
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByOrderNo("SO20240101000099")).thenReturn(Optional.of(order));

        BizException ex = assertThrows(BizException.class,
                () -> useCase.cancel(new CancelOrderCmd("SO20240101000099")));
        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(stockRepository, never()).release(anyLong(), anyInt());
    }

    @Test
    void cancelNotFoundRejected() {
        when(orderRepository.findByOrderNo("NOPE")).thenReturn(Optional.empty());
        BizException ex = assertThrows(BizException.class,
                () -> useCase.cancel(new CancelOrderCmd("NOPE")));
        assertEquals(ErrorCode.PARAM_INVALID, ex.getErrorCode());
    }
}
