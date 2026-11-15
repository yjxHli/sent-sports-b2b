package com.sent.sports.order;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.order.service.DeleteOrderUseCase;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 删除订单用例（淘宝「删除订单」按钮，2026-11-15，TDD 红）。
 *
 * <p>三条口径：</p>
 * <ul>
 *   <li><b>软删</b>：订单是财务与售后的凭证，物理删除会让发票、退款单、应收台账全部悬空，
 *       因此只写 {@code customer_deleted_at}，数据保留在库里；</li>
 *   <li><b>只在终态给删</b>：在途订单（待付款/待发货/待收货）还能退款、还能发货，删掉会让客服找不到单；</li>
 *   <li><b>归属校验</b>：他人的订单与不存在的订单统一 404，不能用 403 泄露「这单存在」。</li>
 * </ul>
 */
class DeleteOrderUseCaseTest extends BaseUnitTest {

    @Mock
    private OrderMapper orderMapper;

    private DeleteOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeleteOrderUseCase(orderMapper);
    }

    private OrderPO order(long id, String status, long customerId) {
        OrderPO po = new OrderPO();
        po.setId(id);
        po.setOrderNo("SO" + id);
        po.setCustomerId(customerId);
        po.setStatus(status);
        po.setPayStatus("PAID");
        return po;
    }

    @Test
    @DisplayName("已完成订单可删除：只写软删时间，不动业务数据")
    void deleteCompletedOrder() {
        when(orderMapper.selectByOrderNo("SO1")).thenReturn(order(1L, "COMPLETED", 9002L));
        when(orderMapper.markCustomerDeleted("SO1", 9002L)).thenReturn(1);

        useCase.delete("SO1", 9002L);

        verify(orderMapper).markCustomerDeleted("SO1", 9002L);
        verify(orderMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("已取消 / 已关闭订单同样可删除")
    void deleteTerminalOrders() {
        when(orderMapper.selectByOrderNo("SO2")).thenReturn(order(2L, "CANCELLED", 9002L));
        when(orderMapper.selectByOrderNo("SO3")).thenReturn(order(3L, "CLOSED", 9002L));
        when(orderMapper.markCustomerDeleted(anyString(), anyLong())).thenReturn(1);

        useCase.delete("SO2", 9002L);
        useCase.delete("SO3", 9002L);

        verify(orderMapper).markCustomerDeleted("SO2", 9002L);
        verify(orderMapper).markCustomerDeleted("SO3", 9002L);
    }

    @Test
    @DisplayName("在途订单不能删：待收货的货还在路上，删了客服就找不到单")
    void cannotDeleteInFlightOrder() {
        when(orderMapper.selectByOrderNo("SO4")).thenReturn(order(4L, "WAIT_RECEIVE", 9002L));

        BizException ex = assertThrows(BizException.class, () -> useCase.delete("SO4", 9002L));

        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(orderMapper, never()).markCustomerDeleted(anyString(), anyLong());
    }

    @Test
    @DisplayName("待付款订单不能删：还没付钱就删，等价于绕过取消释放库存")
    void cannotDeleteWaitPayOrder() {
        when(orderMapper.selectByOrderNo("SO5")).thenReturn(order(5L, "WAIT_PAY", 9002L));

        BizException ex = assertThrows(BizException.class, () -> useCase.delete("SO5", 9002L));

        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("他人的订单一律 404：不能借删除接口探测别人的单号")
    void otherCustomerOrderIsNotFound() {
        when(orderMapper.selectByOrderNo("SO6")).thenReturn(order(6L, "COMPLETED", 9999L));

        BizException ex = assertThrows(BizException.class, () -> useCase.delete("SO6", 9002L));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        verify(orderMapper, never()).markCustomerDeleted(anyString(), anyLong());
    }

    @Test
    @DisplayName("订单不存在 / 未登录：404 与 401，不能 500")
    void missingOrderAndAnonymous() {
        when(orderMapper.selectByOrderNo("NOPE")).thenReturn(null);

        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(BizException.class, () -> useCase.delete("NOPE", 9002L)).getErrorCode());
        assertEquals(ErrorCode.UNAUTHENTICATED,
                assertThrows(BizException.class, () -> useCase.delete("SO1", null)).getErrorCode());
    }

    @Test
    @DisplayName("重复删除：条件更新 0 行按已删除处理，回 404 而不是二次写时间")
    void deleteTwiceIsNotFound() {
        when(orderMapper.selectByOrderNo("SO1")).thenReturn(order(1L, "COMPLETED", 9002L));
        when(orderMapper.markCustomerDeleted("SO1", 9002L)).thenReturn(0);

        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(BizException.class, () -> useCase.delete("SO1", 9002L)).getErrorCode());
    }
}
