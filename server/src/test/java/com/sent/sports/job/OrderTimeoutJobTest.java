package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.order.service.CancelOrderUseCase;
import com.sent.sports.order.service.dto.CancelOrderCmd;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 订单超时取消任务单测（P5-39）：逐单取消、异常单被跳过不中断整轮。 */
@ExtendWith(MockitoExtension.class)
class OrderTimeoutJobTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private CancelOrderUseCase cancelOrderUseCase;

    private OrderTimeoutJob job;

    @BeforeEach
    void setUp() {
        job = new OrderTimeoutJob(orderMapper, cancelOrderUseCase,
                Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("批量取消到期的未支付订单")
    void cancelsAllTimeoutOrders() {
        when(orderMapper.selectTimeoutUnpaid(any(), anyInt()))
                .thenReturn(List.of(order("O0001"), order("O0002")));

        assertThat(job.cancelTimeoutOrders()).isEqualTo(2);

        ArgumentCaptor<CancelOrderCmd> captor = ArgumentCaptor.forClass(CancelOrderCmd.class);
        verify(cancelOrderUseCase, times(2)).cancel(captor.capture());
        assertThat(captor.getAllValues()).extracting(CancelOrderCmd::orderNo)
                .containsExactly("O0001", "O0002");
    }

    @Test
    @DisplayName("单笔已被并发处理（STATE_CONFLICT）不影响其余订单")
    void skipsConflictingOrderAndContinues() {
        when(orderMapper.selectTimeoutUnpaid(any(), anyInt()))
                .thenReturn(List.of(order("O0001"), order("O0002")));
        when(cancelOrderUseCase.cancel(any()))
                .thenThrow(new BizException(ErrorCode.STATE_CONFLICT))
                .thenReturn(null);

        assertThat(job.cancelTimeoutOrders()).isEqualTo(1);
    }

    @Test
    @DisplayName("没有到期订单时不调用取消用例")
    void noopWhenNothingTimeout() {
        when(orderMapper.selectTimeoutUnpaid(any(), anyInt())).thenReturn(List.of());

        assertThat(job.cancelTimeoutOrders()).isZero();
        verify(cancelOrderUseCase, times(0)).cancel(any());
    }

    private OrderPO order(String orderNo) {
        OrderPO po = new OrderPO();
        po.setOrderNo(orderNo);
        return po;
    }
}
