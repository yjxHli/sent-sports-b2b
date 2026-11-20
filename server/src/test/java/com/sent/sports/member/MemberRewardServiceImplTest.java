package com.sent.sports.member;

import com.sent.sports.common.event.DomainEventPublisher;
import com.sent.sports.customer.service.CustomerGrowthService;
import com.sent.sports.customer.service.CustomerPointService;
import com.sent.sports.member.event.MemberRewardEvent;
import com.sent.sports.member.service.impl.MemberRewardServiceImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员奖励发放（TDD 红）：订单完成 → 落成长值与积分流水 → 按落账后的余额提升等级码。
 * <p>缺陷背景 1：成长值一路累加到 16167 而 {@code customer.level_code} 仍是 LV1，
 * 会员中心因此显示「LV1 + 成长值 16167 + 距下一等级还差 -11167」。
 * 等级提升必须发生在发放链路上（只升不降由条件更新兜底），而不是等读面各自猜。</p>
 * <p>缺陷背景 2（P0-R1）：同一链路原先只发成长值，积分只发了个没人监听的事件，
 * 于是自动确认收货的订单永远没有 {@code point_log}。现在积分必须同步落账。</p>
 */
class MemberRewardServiceImplTest extends BaseUnitTest {

    private static final long CUSTOMER_ID = 9002L;

    @Mock
    private DomainEventPublisher eventPublisher;

    @Mock
    private CustomerGrowthService customerGrowthService;

    @Mock
    private CustomerPointService customerPointService;

    @InjectMocks
    private MemberRewardServiceImpl service;

    @Test
    @DisplayName("订单完成：1 元 = 1 成长值 + 1 积分，落账后按新余额提升到 LV2")
    void promotesLevelByBalanceAfterGrowth() {
        when(customerGrowthService.grantGrowth(CUSTOMER_ID, 16167L, "ORDER", "SO-1"))
                .thenReturn(16167L);

        service.grantOrderCompleted(CUSTOMER_ID, "SO-1", 1_616_700L);

        verify(eventPublisher).publish(any(MemberRewardEvent.class));
        verify(customerGrowthService).grantGrowth(CUSTOMER_ID, 16167L, "ORDER", "SO-1");
        verify(customerPointService).changePoints(CUSTOMER_ID, 16167L, "ORDER", "SO-1");
        verify(customerGrowthService).promoteLevel(CUSTOMER_ID, "LV2");
    }

    @Test
    @DisplayName("订单完成：以落账后的余额取档（余额 1050 → LV1），而不是只看本单增量")
    void promotesLevelByReturnedBalanceNotDelta() {
        when(customerGrowthService.grantGrowth(CUSTOMER_ID, 50L, "ORDER", "SO-2"))
                .thenReturn(1050L);

        service.grantOrderCompleted(CUSTOMER_ID, "SO-2", 5000L);

        verify(customerGrowthService).promoteLevel(CUSTOMER_ID, "LV1");
    }

    @Test
    @DisplayName("重放订单（余额未增加）不重复累加，也不触发无意义的升级")
    void replayDoesNotPromoteAgain() {
        when(customerGrowthService.grantGrowth(CUSTOMER_ID, 30L, "ORDER", "SO-3"))
                .thenReturn(120L);

        service.grantOrderCompleted(CUSTOMER_ID, "SO-3", 3000L);

        verify(customerGrowthService, never()).promoteLevel(eq(CUSTOMER_ID), eq("LV1"));
        verify(customerGrowthService, never()).promoteLevel(anyLong(), any());
    }
}
