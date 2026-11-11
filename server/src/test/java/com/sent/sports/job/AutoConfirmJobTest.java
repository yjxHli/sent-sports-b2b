package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 订单自动确认收货任务单测（P5-40）：逐单条件迁移、并发冲突单跳过不中断整轮。 */
@ExtendWith(MockitoExtension.class)
class AutoConfirmJobTest {

    @Mock
    private OrderMapper orderMapper;

    private AutoConfirmJob job;

    @BeforeEach
    void setUp() {
        job = new AutoConfirmJob(orderMapper,
                Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("批量自动确认发货超过 15 天的待收货订单")
    void confirmsAllAwaitingReceiveOrders() {
        when(orderMapper.selectAwaitingReceiveBefore(any(), anyInt()))
                .thenReturn(List.of(order("O0001"), order("O0002")));
        when(orderMapper.transitStatus(anyString(), anyString(), anyString())).thenReturn(1);

        assertThat(job.runOnce()).isEqualTo(2);

        ArgumentCaptor<String> orderNos = ArgumentCaptor.forClass(String.class);
        verify(orderMapper, times(2)).transitStatus(orderNos.capture(), eq("WAIT_RECEIVE"), eq("COMPLETED"));
        assertThat(orderNos.getAllValues()).containsExactly("O0001", "O0002");
    }

    @Test
    @DisplayName("单笔已被并发处理（STATE_CONFLICT）不影响其余订单")
    void skipsConflictingOrderAndContinues() {
        when(orderMapper.selectAwaitingReceiveBefore(any(), anyInt()))
                .thenReturn(List.of(order("O0001"), order("O0002")));
        when(orderMapper.transitStatus(anyString(), anyString(), anyString()))
                .thenThrow(new BizException(ErrorCode.STATE_CONFLICT))
                .thenReturn(1);

        assertThat(job.runOnce()).isEqualTo(1);
    }

    @Test
    @DisplayName("没有待确认订单时不动作")
    void noopWhenNothingToConfirm() {
        when(orderMapper.selectAwaitingReceiveBefore(any(), anyInt())).thenReturn(List.of());

        assertThat(job.runOnce()).isZero();
        verify(orderMapper, never()).transitStatus(anyString(), anyString(), anyString());
    }

    private OrderPO order(String orderNo) {
        OrderPO po = new OrderPO();
        po.setOrderNo(orderNo);
        return po;
    }
}
