package com.sent.sports.customize;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.mapper.po.CustomOrderPO;
import com.sent.sports.customize.service.BatchDeleteCustomOrdersUseCase;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

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
 * 我的定制「多选删除」（2026-11-21 用户要求，TDD 红）。
 *
 * <p>定制单与商品订单不同：审核/生产中的单承载着图稿与审图 SLA，客户删掉会让审图台凭空少一条待办，
 * 因此只有 {@code CustomStatus#isTerminal()} 的状态（已发货 SHIPPED / 已取消 CANCELLED）可删；
 * 其余状态跳过并回报原因，整批不会因为一条不可删而失败。</p>
 */
class BatchDeleteCustomOrdersUseCaseTest extends BaseUnitTest {

    @Mock
    private CustomOrderMapper customOrderMapper;

    private BatchDeleteCustomOrdersUseCase useCase;

    /** 可删状态取自 {@code CustomStatus.isTerminal()}，桩不硬编码状态名。 */
    private static final List<String> DELETABLE = BatchDeleteCustomOrdersUseCase.DELETABLE_STATUSES;

    @BeforeEach
    void setUp() {
        useCase = new BatchDeleteCustomOrdersUseCase(customOrderMapper);
    }

    private CustomOrderPO custom(String no, String status, long customerId) {
        CustomOrderPO po = new CustomOrderPO();
        po.setCustomNo(no);
        po.setStatus(status);
        po.setCustomerId(customerId);
        return po;
    }

    @Test
    @DisplayName("只删终态（已发货 / 已取消），生产中的单跳过")
    void deletesOnlyTerminalStatus() {
        when(customOrderMapper.selectByCustomNosForCustomer(9002L, List.of("CU1", "CU2", "CU3")))
                .thenReturn(List.of(custom("CU1", "SHIPPED", 9002L),
                        custom("CU2", "IN_PRODUCTION", 9002L),
                        custom("CU3", "CANCELLED", 9002L)));
        when(customOrderMapper.batchMarkCustomerDeleted(9002L, List.of("CU1", "CU3"), DELETABLE)).thenReturn(2);

        var result = useCase.delete(List.of("CU3", "CU1", "CU2"), 9002L);

        assertEquals(2, result.deleted());
        assertEquals(1, result.skipped().size());
        assertEquals("CU2", result.skipped().get(0).orderNo());
        assertTrue(result.skipped().get(0).reason().contains("进行中"),
                "进行中的定制单要说清为什么删不了：实际 " + result.skipped().get(0).reason());
        assertEquals(List.of("SHIPPED", "CANCELLED"), DELETABLE,
                "可删范围必须跟着 CustomStatus 的终态走：新增终态时这里会先红，提醒复核删除口径");
    }

    @Test
    @DisplayName("待提交图稿 / 待审核同样不可删：客户还要传图，客服还要审图")
    void keepsActionableStatuses() {
        when(customOrderMapper.selectByCustomNosForCustomer(9002L, List.of("CU1", "CU2")))
                .thenReturn(List.of(custom("CU1", "WAIT_DRAFT", 9002L), custom("CU2", "WAIT_REVIEW", 9002L)));

        var result = useCase.delete(List.of("CU1", "CU2"), 9002L);

        assertEquals(0, result.deleted());
        assertEquals(2, result.skipped().size());
        verify(customOrderMapper, never()).batchMarkCustomerDeleted(anyLong(), anyList(), anyList());
    }

    @Test
    @DisplayName("不存在 / 他人的定制单跳过，不泄露是否存在")
    void skipsUnknownOrForeignCustomOrder() {
        when(customOrderMapper.selectByCustomNosForCustomer(9002L, List.of("CU1", "OTHER")))
                .thenReturn(List.of(custom("CU1", "SHIPPED", 9002L)));
        when(customOrderMapper.batchMarkCustomerDeleted(9002L, List.of("CU1"), DELETABLE)).thenReturn(1);

        var result = useCase.delete(List.of("CU1", "OTHER"), 9002L);

        assertEquals(1, result.deleted());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).reason().contains("不存在"));
    }

    @Test
    @DisplayName("重复删除：影响 0 行按幂等成功返回")
    void alreadyDeletedIsIdempotent() {
        when(customOrderMapper.selectByCustomNosForCustomer(9002L, List.of("CU1")))
                .thenReturn(List.of(custom("CU1", "SHIPPED", 9002L)));
        when(customOrderMapper.batchMarkCustomerDeleted(9002L, List.of("CU1"), DELETABLE)).thenReturn(0);

        var result = useCase.delete(List.of("CU1"), 9002L);

        assertEquals(0, result.deleted());
        assertTrue(result.skipped().isEmpty());
    }

    @Test
    @DisplayName("空选择 / 未登录：参数错误与 401")
    void rejectsEmptySelectionAndAnonymous() {
        assertEquals(ErrorCode.PARAM_INVALID,
                assertThrows(BizException.class, () -> useCase.delete(List.of(), 9002L)).getErrorCode());
        assertEquals(ErrorCode.UNAUTHENTICATED,
                assertThrows(BizException.class, () -> useCase.delete(List.of("CU1"), null)).getErrorCode());
        verify(customOrderMapper, never()).batchMarkCustomerDeleted(anyLong(), any(), anyList());
    }
}
