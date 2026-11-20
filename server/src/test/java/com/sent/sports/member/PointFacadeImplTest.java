package com.sent.sports.member;

import com.sent.sports.customer.service.CustomerPointService;
import com.sent.sports.member.service.impl.PointFacadeImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 积分门面单测（P0-R4）：退款扣回是同步的真实落账，不再是「发个没人听的事件」。
 */
class PointFacadeImplTest extends BaseUnitTest {

    private static final long CUSTOMER_ID = 9002L;

    @Mock
    private CustomerPointService customerPointService;

    @InjectMocks
    private PointFacadeImpl facade;

    @Test
    @DisplayName("退款 242 元 → 同步扣回 242 积分，来源单号用退款单号")
    void revokeForRefundDeductsSynchronously() {
        long revoked = facade.revokeForRefund(CUSTOMER_ID, "RF-1", 24_200L);

        assertEquals(242L, revoked);
        verify(customerPointService).changePoints(CUSTOMER_ID, -242L, "REFUND", "RF-1");
    }

    @Test
    @DisplayName("不足 1 元没有对应积分可扣，不写空流水")
    void lessThanOneYuanWritesNothing() {
        assertEquals(0L, facade.revokeForRefund(CUSTOMER_ID, "RF-2", 99L));
        verify(customerPointService, never()).changePoints(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("抵扣上限仍由领域 PointDeduction 计算（上限 30%）")
    void maxDeductibleStillDelegatesToDomain() {
        // 可用 5000 分可抵 5000 分，但上限为商品额 10000 × 30% = 3000
        assertEquals(3000L, facade.maxDeductibleCent(5000, 10_000L));
    }

    @Test
    @DisplayName("支付成功扣积分：金额（分）1:1 折积分后交给台账，来源单号用订单号")
    void deductPointsDelegatesOnePointPerCent() {
        when(customerPointService.deductPoints(CUSTOMER_ID, 3000L, "ORDER_USE", "SO-1"))
                .thenReturn(47_000L);

        long after = facade.deductPoints(CUSTOMER_ID, "SO-1", 3000L);

        assertEquals(47_000L, after, "返回台账扣减后的余额");
        // 关键：3000 分抵扣扣 3000 积分，不是 30（发放侧才是 1 元 = 1 积分）
        verify(customerPointService).deductPoints(CUSTOMER_ID, 3000L, "ORDER_USE", "SO-1");
    }

    @Test
    @DisplayName("退款退回积分：正增量入账，来源单号用退款单号")
    void returnForRefundAddsPoints() {
        facade.returnForRefund(CUSTOMER_ID, "RF-1", 3000L);
        verify(customerPointService).changePoints(CUSTOMER_ID, 3000L, "REFUND_RETURN", "RF-1");
    }

    @Test
    @DisplayName("应退积分为 0：不写空流水")
    void zeroReturnWritesNothing() {
        facade.returnForRefund(CUSTOMER_ID, "RF-2", 0L);
        verify(customerPointService, never()).changePoints(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("订单已扣积分查询：按 ORDER_USE 来源查台账")
    void deductedPointsForOrderReadsLedger() {
        when(customerPointService.deductedPoints(CUSTOMER_ID, "ORDER_USE", "SO-1")).thenReturn(3000L);
        assertEquals(3000L, facade.deductedPointsForOrder(CUSTOMER_ID, "SO-1"));
    }

    @Test
    @DisplayName("本单没有积分抵扣（0 分）：不产生任何流水")
    void zeroDeductionWritesNothing() {
        assertEquals(0L, facade.deductPoints(CUSTOMER_ID, "SO-2", 0L));
        verify(customerPointService, never()).deductPoints(anyLong(), anyLong(), anyString(), anyString());
    }
}
