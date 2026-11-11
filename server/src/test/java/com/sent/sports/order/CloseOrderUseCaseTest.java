package com.sent.sports.order;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.service.CreditFacade;
import com.sent.sports.order.domain.PayStatus;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.order.service.CloseOrderUseCase;
import com.sent.sports.order.service.dto.CloseOrderCmd;
import com.sent.sports.order.service.dto.CloseOrderResult;
import com.sent.sports.refund.service.RefundService;
import com.sent.sports.stock.repository.StockRepository;
import com.sent.sports.system.service.SystemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 管理端关闭订单用例（红→绿）：状态机 / 全额退款 / 库存补偿 / 授信释放 / 操作日志。
 * <p>只允许 WAIT_SHIP、PART_SHIPPED 关单；WAIT_RECEIVE 及以上必须走退货流程。</p>
 */
@ExtendWith(MockitoExtension.class)
class CloseOrderUseCaseTest {

    private static final String ORDER_NO = "SO20261108000999";
    private static final String REASON = "客户取消，缺货";

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private RefundService refundService;
    @Mock
    private CreditFacade creditFacade;
    @Mock
    private SystemService systemService;

    private CloseOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CloseOrderUseCase(orderMapper, stockRepository, refundService,
                creditFacade, systemService);
    }

    @Test
    @DisplayName("WAIT_SHIP 关单：条件迁移 + 全额退款 + 释放预占库存 + 释放授信 + 留痕")
    void closeWaitShipDoesFullClose() {
        OrderPO po = order("WAIT_SHIP", PayStatus.PAID, 20000L);
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(po);
        when(orderMapper.closeOnAdminRefund(eq(ORDER_NO), eq("WAIT_SHIP"),
                eq("FULL_REFUNDED"), eq("PAID"))).thenReturn(1);
        when(refundService.createCloseRefund(ORDER_NO, 11L, 20000L, REASON))
                .thenReturn("RF20261108000123");

        CloseOrderResult result = useCase.close(new CloseOrderCmd(ORDER_NO, REASON, 9003L));

        assertEquals(ORDER_NO, result.orderNo());
        assertEquals("CLOSED", result.status());
        assertEquals("RF20261108000123", result.refundNo());
        assertEquals(20000L, result.refundedCent());
        assertEquals(20000L, result.payableCent());
        assertTrue(result.stockReleased());
        verify(stockRepository).releaseByOrderNo(ORDER_NO);
        verify(creditFacade).releaseCreditForOrder(ORDER_NO);
        verify(systemService).recordLog("ORDER", "CLOSE", "ORDER", ORDER_NO, REASON, 9003L);
    }

    @Test
    @DisplayName("PART_SHIPPED 可关单，但已发部分不再释放预占库存")
    void closePartShippedSkipsStockRelease() {
        OrderPO po = order("PART_SHIPPED", PayStatus.PAID, 20000L);
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(po);
        when(orderMapper.closeOnAdminRefund(eq(ORDER_NO), eq("PART_SHIPPED"),
                eq("FULL_REFUNDED"), eq("PAID"))).thenReturn(1);
        when(refundService.createCloseRefund(ORDER_NO, 11L, 20000L, REASON))
                .thenReturn("RF20261108000124");

        CloseOrderResult result = useCase.close(new CloseOrderCmd(ORDER_NO, REASON, null));

        assertFalse(result.stockReleased());
        verify(stockRepository, never()).releaseByOrderNo(anyString());
        verify(refundService).createCloseRefund(anyString(), anyLong(), eq(20000L), eq(REASON));
    }

    @Test
    @DisplayName("WAIT_RECEIVE 已全部发货：拒绝关单，提示走退货流程，且不产生任何写操作")
    void rejectWaitReceive() {
        when(orderMapper.selectByOrderNo(ORDER_NO))
                .thenReturn(order("WAIT_RECEIVE", PayStatus.PAID, 20000L));

        BizException ex = assertThrows(BizException.class,
                () -> useCase.close(new CloseOrderCmd(ORDER_NO, REASON, null)));

        assertEquals(ErrorCode.STATE_CONFLICT, ex.getErrorCode());
        verify(orderMapper, never()).closeOnAdminRefund(anyString(), anyString(), anyString(), anyString());
        verifyNoInteractions(refundService, stockRepository, creditFacade, systemService);
    }

    @Test
    @DisplayName("COMPLETED / CANCELLED 等终态一律拒绝")
    void rejectTerminalStatus() {
        for (String status : new String[]{"COMPLETED", "CANCELLED", "CLOSED", "WAIT_PAY"}) {
            when(orderMapper.selectByOrderNo(ORDER_NO))
                    .thenReturn(order(status, PayStatus.PAID, 20000L));
            assertEquals(ErrorCode.STATE_CONFLICT,
                    assertThrows(BizException.class,
                            () -> useCase.close(new CloseOrderCmd(ORDER_NO, REASON, null)))
                            .getErrorCode(),
                    status + " 不应允许关单");
        }
        verifyNoInteractions(refundService, stockRepository, creditFacade, systemService);
    }

    @Test
    @DisplayName("支付维度必须按 PayStateMachine 迁移：UNPAID 不允许全额退款")
    void rejectUnpaidOrder() {
        when(orderMapper.selectByOrderNo(ORDER_NO))
                .thenReturn(order("WAIT_SHIP", PayStatus.UNPAID, 0L));

        assertEquals(ErrorCode.STATE_CONFLICT,
                assertThrows(BizException.class,
                        () -> useCase.close(new CloseOrderCmd(ORDER_NO, REASON, null)))
                        .getErrorCode());
        verifyNoInteractions(refundService, stockRepository, creditFacade, systemService);
    }

    @Test
    @DisplayName("原因必填：空/空白直接拒绝，且不产生写操作")
    void rejectBlankReason() {
        for (String reason : new String[]{null, "", "   "}) {
            assertEquals(ErrorCode.PARAM_INVALID,
                    assertThrows(BizException.class,
                            () -> useCase.close(new CloseOrderCmd(ORDER_NO, reason, null)))
                            .getErrorCode());
        }
        verifyNoInteractions(orderMapper, refundService, stockRepository, creditFacade, systemService);
    }

    @Test
    @DisplayName("订单不存在返回 NOT_FOUND")
    void rejectMissingOrder() {
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(null);

        assertEquals(ErrorCode.NOT_FOUND,
                assertThrows(BizException.class,
                        () -> useCase.close(new CloseOrderCmd(ORDER_NO, REASON, null)))
                        .getErrorCode());
    }

    @Test
    @DisplayName("并发下条件更新 0 行：拒绝重复关单，不得退款/释放库存")
    void rejectConcurrentClose() {
        when(orderMapper.selectByOrderNo(ORDER_NO))
                .thenReturn(order("WAIT_SHIP", PayStatus.PAID, 20000L));
        when(orderMapper.closeOnAdminRefund(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(0);

        assertEquals(ErrorCode.STATE_CONFLICT,
                assertThrows(BizException.class,
                        () -> useCase.close(new CloseOrderCmd(ORDER_NO, REASON, null)))
                        .getErrorCode());
        verifyNoInteractions(refundService, stockRepository, creditFacade, systemService);
    }

    @Test
    @DisplayName("实付额缺失时以应付额兜底（演示数据可能只写 pay_status 未写 paid_cent）")
    void fallsBackToPayableCent() {
        OrderPO po = order("WAIT_SHIP", PayStatus.PAID, 0L);
        po.setPayableCent(18800L);
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(po);
        when(orderMapper.closeOnAdminRefund(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1);
        when(refundService.createCloseRefund(ORDER_NO, 11L, 18800L, REASON))
                .thenReturn("RF-FALLBACK");

        CloseOrderResult result = useCase.close(new CloseOrderCmd(ORDER_NO, REASON, null));

        assertEquals(18800L, result.refundedCent());
    }

    @Test
    @DisplayName("无主键的历史脏数据拒绝关单，避免退款单外键落到 0")
    void rejectOrderWithoutPrimaryKey() {
        OrderPO po = order("WAIT_SHIP", PayStatus.PAID, 20000L);
        po.setId(null);
        when(orderMapper.selectByOrderNo(ORDER_NO)).thenReturn(po);

        assertEquals(ErrorCode.STATE_CONFLICT,
                assertThrows(BizException.class,
                        () -> useCase.close(new CloseOrderCmd(ORDER_NO, REASON, null)))
                        .getErrorCode());
        verifyNoInteractions(refundService, stockRepository, creditFacade, systemService);
    }

    private OrderPO order(String status, PayStatus payStatus, long paidCent) {
        OrderPO po = new OrderPO();
        po.setId(11L);
        po.setOrderNo(ORDER_NO);
        po.setCustomerId(9002L);
        po.setStatus(status);
        po.setPayStatus(payStatus.name());
        po.setPayableCent(20000L);
        po.setPaidCent(paidCent);
        return po;
    }
}
