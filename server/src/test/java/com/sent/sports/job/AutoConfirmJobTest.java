package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.order.service.ConfirmReceiptUseCase;
import com.sent.sports.order.service.OrderJobQueryService;
import com.sent.sports.order.service.dto.ConfirmReceiptCmd;
import com.sent.sports.order.service.dto.ConfirmReceiptResult;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单自动确认收货任务单测（P5-40 / P0-R1）：
 * 逐单委托 {@link ConfirmReceiptUseCase}（不再直连 mapper 改状态），并发冲突单跳过不中断整轮。
 */
@ExtendWith(MockitoExtension.class)
class AutoConfirmJobTest {

    @Mock
    private OrderJobQueryService orderJobQueryService;
    @Mock
    private ConfirmReceiptUseCase confirmReceiptUseCase;

    private AutoConfirmJob job;

    @BeforeEach
    void setUp() {
        job = new AutoConfirmJob(orderJobQueryService, confirmReceiptUseCase,
                Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("自动确认阈值收紧为 7 天（原 15 天）")
    void usesSevenDayThreshold() {
        when(orderJobQueryService.findAwaitingReceiveOrderNos(any(), anyInt())).thenReturn(List.of());

        job.runOnce();

        ArgumentCaptor<java.time.LocalDateTime> before = ArgumentCaptor.forClass(java.time.LocalDateTime.class);
        verify(orderJobQueryService).findAwaitingReceiveOrderNos(before.capture(), anyInt());
        assertThat(before.getValue()).as("用户要求：已送达的待收货订单超过 7 天自动收货")
                .isEqualTo(java.time.LocalDateTime.parse("2026-10-28T02:00:00"));
    }

    @Test
    @DisplayName("每单都走 ConfirmReceiptUseCase（积分/成长值/应收的副作用由此产生）")
    void delegatesEveryOrderToConfirmReceiptUseCase() {
        when(orderJobQueryService.findAwaitingReceiveOrderNos(any(), anyInt()))
                .thenReturn(List.of("O0001", "O0002"));
        when(confirmReceiptUseCase.confirm(any())).thenReturn(completed("O0001"), completed("O0002"));

        assertThat(job.runOnce()).isEqualTo(2);

        ArgumentCaptor<ConfirmReceiptCmd> cmds = ArgumentCaptor.forClass(ConfirmReceiptCmd.class);
        verify(confirmReceiptUseCase, times(2)).confirm(cmds.capture());
        assertThat(cmds.getAllValues())
                .extracting(ConfirmReceiptCmd::orderNo)
                .containsExactly("O0001", "O0002");
        assertThat(cmds.getAllValues())
                .allSatisfy(cmd -> assertThat(cmd.lines())
                        .as("自动收货按「已发未收」全量确认，不带明细")
                        .isEmpty());
    }

    @Test
    @DisplayName("单笔已被并发处理（STATE_CONFLICT）不影响其余订单")
    void skipsConflictingOrderAndContinues() {
        when(orderJobQueryService.findAwaitingReceiveOrderNos(any(), anyInt()))
                .thenReturn(List.of("O0001", "O0002"));
        when(confirmReceiptUseCase.confirm(any()))
                .thenThrow(new BizException(ErrorCode.STATE_CONFLICT))
                .thenReturn(completed("O0002"));

        assertThat(job.runOnce()).isEqualTo(1);
    }

    @Test
    @DisplayName("尚未全部收货（completed=false）不计入确认数")
    void partialReceiptIsNotCounted() {
        when(orderJobQueryService.findAwaitingReceiveOrderNos(any(), anyInt())).thenReturn(List.of("O0001"));
        when(confirmReceiptUseCase.confirm(any()))
                .thenReturn(new ConfirmReceiptResult("O0001", "WAIT_RECEIVE", false));

        assertThat(job.runOnce()).isZero();
    }

    @Test
    @DisplayName("没有待确认订单时不动作")
    void noopWhenNothingToConfirm() {
        when(orderJobQueryService.findAwaitingReceiveOrderNos(any(), anyInt())).thenReturn(List.of());

        assertThat(job.runOnce()).isZero();
        verify(confirmReceiptUseCase, never()).confirm(any());
    }

    private ConfirmReceiptResult completed(String orderNo) {
        return new ConfirmReceiptResult(orderNo, "COMPLETED", true);
    }
}
