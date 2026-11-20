package com.sent.sports.customer;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customer.mapper.CustomerMapper;
import com.sent.sports.customer.mapper.po.CustomerPO;
import com.sent.sports.customer.service.impl.CustomerPointServiceImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 积分台账真库实现单测（P0-R1 / R4）：先抢唯一键名额，再累加余额；重放不二次入账。
 */
class CustomerPointServiceImplTest extends BaseUnitTest {

    private static final long CUSTOMER_ID = 9002L;

    @Mock
    private CustomerMapper customerMapper;

    @InjectMocks
    private CustomerPointServiceImpl service;

    @Test
    @DisplayName("首次入账：写 point_log 后累加余额，返回新余额")
    void firstChangeWritesLogThenAddsBalance() {
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer(1000L));
        when(customerMapper.insertPointLogIfAbsent(any())).thenReturn(1);
        when(customerMapper.addPoints(CUSTOMER_ID, 250L)).thenReturn(1);

        long after = service.changePoints(CUSTOMER_ID, 250L, "ORDER", "SO-1");

        assertEquals(1250L, after);
        ArgumentCaptor<CustomerMapper.PointLogRow> row =
                ArgumentCaptor.forClass(CustomerMapper.PointLogRow.class);
        verify(customerMapper).insertPointLogIfAbsent(row.capture());
        assertEquals(250L, row.getValue().delta());
        assertEquals(1250L, row.getValue().balanceAfter());
        assertEquals("ORDER", row.getValue().type());
        assertEquals("SO-1", row.getValue().sourceId());
        verify(customerMapper).addPoints(CUSTOMER_ID, 250L);
    }

    @Test
    @DisplayName("同一来源重放：流水插入返回 0，余额不再累加")
    void replayDoesNotTouchBalance() {
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer(1000L));
        when(customerMapper.insertPointLogIfAbsent(any())).thenReturn(0);

        long after = service.changePoints(CUSTOMER_ID, 250L, "ORDER", "SO-1");

        assertEquals(1000L, after, "重放返回当前余额");
        verify(customerMapper, never()).addPoints(anyLong(), anyLong());
    }

    @Test
    @DisplayName("退款扣回允许负余额（spec §6.4：已消耗的积分记为负）")
    void negativeDeltaAllowed() {
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(customer(30L));
        when(customerMapper.insertPointLogIfAbsent(any())).thenReturn(1);
        when(customerMapper.addPoints(CUSTOMER_ID, -100L)).thenReturn(1);

        assertEquals(-70L, service.changePoints(CUSTOMER_ID, -100L, "REFUND", "RF-1"));
    }

    @Test
    @DisplayName("客户不存在时不制造孤立流水")
    void missingCustomerWritesNothing() {
        when(customerMapper.selectById(CUSTOMER_ID)).thenReturn(null);

        assertEquals(0L, service.changePoints(CUSTOMER_ID, 100L, "ORDER", "SO-1"));
        verify(customerMapper, never()).insertPointLogIfAbsent(any());
        verify(customerMapper, never()).addPoints(anyLong(), anyLong());
    }

    @Test
    @DisplayName("没有来源单号的流水无法幂等，直接拒绝")
    void blankSourceRejected() {
        BizException e = assertThrows(BizException.class,
                () -> service.changePoints(CUSTOMER_ID, 100L, "ORDER", " "));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(customerMapper, never()).insertPointLogIfAbsent(any());
    }

    @Test
    @DisplayName("零变动不写流水")
    void zeroDeltaRejected() {
        assertThrows(BizException.class,
                () -> service.changePoints(CUSTOMER_ID, 0L, "ORDER", "SO-1"));
    }

    private CustomerPO customer(long pointsBalance) {
        CustomerPO po = new CustomerPO();
        po.setId(CUSTOMER_ID);
        po.setPointsBalance(pointsBalance);
        po.setDeleted(0);
        return po;
    }

    // ==================== 支付成功扣减积分（P5 闭环） ====================

    @Test
    @DisplayName("支付抵扣：先抢流水名额，再条件更新扣减余额，流水 type=USE 且金额为负")
    void deductWritesUseLogThenSubtractsBalance() {
        when(customerMapper.insertPointLogIfAbsent(any())).thenReturn(1);
        when(customerMapper.deductPoints(CUSTOMER_ID, 3000L)).thenReturn(1);
        when(customerMapper.pointsBalanceOf(CUSTOMER_ID)).thenReturn(47_000L);

        long after = service.deductPoints(CUSTOMER_ID, 3000L, "ORDER_USE", "SO-1");

        assertEquals(47_000L, after, "返回扣减后的真实余额");
        ArgumentCaptor<CustomerMapper.PointLogRow> row =
                ArgumentCaptor.forClass(CustomerMapper.PointLogRow.class);
        verify(customerMapper).insertPointLogIfAbsent(row.capture());
        assertEquals(-3000L, row.getValue().delta(), "扣减在流水里必须是负数");
        assertEquals("USE", row.getValue().type(), "抵扣流水类型固定 USE");
        assertEquals("ORDER_USE", row.getValue().sourceType());
        assertEquals("SO-1", row.getValue().sourceId(), "必须关联订单号");
        // balance_after 只能扣完才知道：先占位，再回填
        verify(customerMapper).updatePointLogBalance(CUSTOMER_ID, "ORDER_USE", "SO-1", 47_000L);
    }

    @Test
    @DisplayName("并发导致余额不足（条件更新 0 行）：抛 POINT_NOT_ENOUGH，绝不写负余额")
    void deductBlockedWhenBalanceInsufficient() {
        when(customerMapper.insertPointLogIfAbsent(any())).thenReturn(1);
        when(customerMapper.deductPoints(CUSTOMER_ID, 3000L)).thenReturn(0);
        when(customerMapper.pointsBalanceOf(CUSTOMER_ID)).thenReturn(100L);

        BizException e = assertThrows(BizException.class,
                () -> service.deductPoints(CUSTOMER_ID, 3000L, "ORDER_USE", "SO-1"));

        assertEquals(ErrorCode.POINT_NOT_ENOUGH, e.getErrorCode());
        verify(customerMapper, never()).updatePointLogBalance(anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("同一订单重复回调：流水名额已被占，连扣减 SQL 都不发")
    void deductReplayDoesNotSubtractAgain() {
        when(customerMapper.insertPointLogIfAbsent(any())).thenReturn(0);
        when(customerMapper.pointsBalanceOf(CUSTOMER_ID)).thenReturn(1000L);

        assertEquals(1000L, service.deductPoints(CUSTOMER_ID, 3000L, "ORDER_USE", "SO-1"));
        verify(customerMapper, never()).deductPoints(anyLong(), anyLong());
    }

    @Test
    @DisplayName("查询已扣积分：以台账为准，没扣过返回 0，缺来源单号直接拒绝")
    void deductedPointsReadsLedger() {
        when(customerMapper.deductedPointsOf(CUSTOMER_ID, "ORDER_USE", "SO-1")).thenReturn(3000L, (Long) null);

        assertEquals(3000L, service.deductedPoints(CUSTOMER_ID, "ORDER_USE", "SO-1"));
        assertEquals(0L, service.deductedPoints(CUSTOMER_ID, "ORDER_USE", "SO-1"));
        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> service.deductedPoints(CUSTOMER_ID, "ORDER_USE", "")).getErrorCode());
    }

    @Test
    @DisplayName("扣减额必须为正数，缺来源单号直接拒绝")
    void invalidDeductRejected() {
        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> service.deductPoints(CUSTOMER_ID, 0L, "ORDER_USE", "SO-1")).getErrorCode());
        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> service.deductPoints(CUSTOMER_ID, -100L, "ORDER_USE", "SO-1")).getErrorCode());
        assertEquals(ErrorCode.PARAM_INVALID, assertThrows(BizException.class,
                () -> service.deductPoints(CUSTOMER_ID, 100L, "ORDER_USE", " ")).getErrorCode());
        verify(customerMapper, never()).deductPoints(anyLong(), anyLong());
    }
}
