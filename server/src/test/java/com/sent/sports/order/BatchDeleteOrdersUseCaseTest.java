package com.sent.sports.order;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.order.service.BatchDeleteOrdersUseCase;
import com.sent.sports.order.service.DeleteOrderUseCase;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 我的订单「多选删除」（2026-11-21 用户要求，TDD 红）。
 *
 * <p>与单条删除（{@link com.sent.sports.order.service.DeleteOrderUseCase}）同一套业务口径，
 * 只是把「选中的一批」拆成可删与不可删两部分，**不能因为其中一单在途就把整批拒掉**：</p>
 * <ul>
 *   <li>软删：只写 {@code customer_deleted_at}，财务/客服/售后读面照旧；</li>
 *   <li>只在终态可删（COMPLETED / CANCELLED / CLOSED），在途单跳过并回报原因；</li>
 *   <li>归属校验：他人订单与不存在的订单表现一致（不进结果、按「不存在或已删除」跳过）；</li>
 *   <li>幂等：重复提交已删过的单，删除数为 0 而不是报错。</li>
 * </ul>
 */
class BatchDeleteOrdersUseCaseTest extends BaseUnitTest {

    @Mock
    private OrderMapper orderMapper;

    private BatchDeleteOrdersUseCase useCase;

    /** 与用例共用同一份终态清单：桩里不硬编码状态名，避免「改了常量但测试还在断言旧值」。 */
    private static final List<String> DELETABLE = DeleteOrderUseCase.DELETABLE_STATUSES;

    @BeforeEach
    void setUp() {
        useCase = new BatchDeleteOrdersUseCase(orderMapper);
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
    @DisplayName("混合选择：只删终态单，在途单跳过并给出原因")
    void deletesOnlyTerminalAndReportsSkipped() {
        when(orderMapper.selectByOrderNosForCustomer(9002L, List.of("SO1", "SO2", "SO3")))
                .thenReturn(List.of(order(1L, "COMPLETED", 9002L),
                        order(2L, "WAIT_SHIP", 9002L),
                        order(3L, "CANCELLED", 9002L)));
        when(orderMapper.batchMarkCustomerDeleted(9002L, List.of("SO1", "SO3"), DELETABLE)).thenReturn(2);

        var result = useCase.delete(List.of("SO3", "SO1", "SO2", "SO1"), 9002L);

        assertEquals(2, result.deleted(), "两笔终态单应删掉（重复勾选去重、顺序不影响）");
        assertEquals(1, result.skipped().size());
        assertEquals("SO2", result.skipped().get(0).orderNo());
        assertTrue(result.skipped().get(0).reason().contains("在途"),
                "在途订单要明确告诉用户为什么没删：实际 " + result.skipped().get(0).reason());
    }

    @Test
    @DisplayName("不存在 / 他人的订单：按「不存在」跳过，绝不落到删除 SQL 里")
    void skipsUnknownOrForeignOrders() {
        // 归一化是「字典序升序」，桩必须写排序后的形态，否则 Mockito 严格模式会报参数不匹配
        when(orderMapper.selectByOrderNosForCustomer(9002L, List.of("OTHER", "SO1")))
                .thenReturn(List.of(order(1L, "COMPLETED", 9002L)));
        when(orderMapper.batchMarkCustomerDeleted(9002L, List.of("SO1"), DELETABLE)).thenReturn(1);

        var result = useCase.delete(List.of("SO1", "OTHER"), 9002L);

        assertEquals(1, result.deleted());
        assertEquals(1, result.skipped().size());
        assertEquals("OTHER", result.skipped().get(0).orderNo());
        assertTrue(result.skipped().get(0).reason().contains("不存在"),
                "他人单与不存在单用同一句话，不能泄露单号是否存在");
        verify(orderMapper).batchMarkCustomerDeleted(9002L, List.of("SO1"), DELETABLE);
    }

    @Test
    @DisplayName("全是在途单：一条删除 SQL 都不发，只回报跳过明细")
    void noUpdateWhenNothingDeletable() {
        when(orderMapper.selectByOrderNosForCustomer(9002L, List.of("SO1", "SO2")))
                .thenReturn(List.of(order(1L, "WAIT_PAY", 9002L), order(2L, "WAIT_RECEIVE", 9002L)));

        var result = useCase.delete(List.of("SO1", "SO2"), 9002L);

        assertEquals(0, result.deleted());
        assertEquals(2, result.skipped().size());
        verify(orderMapper, never()).batchMarkCustomerDeleted(anyLong(), anyList(), anyList());
    }

    @Test
    @DisplayName("重复提交已删过的单：删除 0 条按幂等成功返回，不抛异常")
    void alreadyDeletedIsIdempotent() {
        when(orderMapper.selectByOrderNosForCustomer(9002L, List.of("SO1")))
                .thenReturn(List.of(order(1L, "COMPLETED", 9002L)));
        // 条件更新带 customer_deleted_at IS NULL：第二次点删除影响 0 行
        when(orderMapper.batchMarkCustomerDeleted(9002L, List.of("SO1"), DELETABLE)).thenReturn(0);

        var result = useCase.delete(List.of("SO1"), 9002L);

        assertEquals(0, result.deleted());
        assertTrue(result.skipped().isEmpty(), "同一批里没有「不可删」的单，只是已被删过");
    }

    @Test
    @DisplayName("空选择 / 未登录：参数错误与 401，不能 500")
    void rejectsEmptySelectionAndAnonymous() {
        assertEquals(ErrorCode.PARAM_INVALID,
                assertThrows(BizException.class, () -> useCase.delete(List.of(), 9002L)).getErrorCode());
        // 空白串与 null 都是前端「勾了又取消」的产物：归一后为空，按参数错误拒绝
        assertEquals(ErrorCode.PARAM_INVALID,
                assertThrows(BizException.class,
                        () -> useCase.delete(Arrays.asList("  ", null), 9002L)).getErrorCode());
        assertEquals(ErrorCode.UNAUTHENTICATED,
                assertThrows(BizException.class, () -> useCase.delete(List.of("SO1"), null)).getErrorCode());
        verify(orderMapper, never()).batchMarkCustomerDeleted(anyLong(), any(), anyList());
    }
}
