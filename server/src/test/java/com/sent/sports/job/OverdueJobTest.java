package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.service.ReceivableJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 应收逾期重算任务单测（P5-41）：逐笔更新状态、并发冲突笔跳过不中断整轮。 */
@ExtendWith(MockitoExtension.class)
class OverdueJobTest {

    @Mock
    private ReceivableJobService receivableJobService;

    private OverdueJob job;

    @BeforeEach
    void setUp() {
        job = new OverdueJob(receivableJobService,
                Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("逾期应收批量置为 OVERDUE，已是 OVERDUE 的跳过")
    void marksOverdueReceivables() {
        // 已是 OVERDUE 的由 finance 服务过滤，这里只拿到待更新的 1、2
        when(receivableJobService.findOverdueIds(any())).thenReturn(List.of(1L, 2L));
        when(receivableJobService.markOverdue(anyLong())).thenReturn(1);

        assertThat(job.runOnce()).isEqualTo(2);
        verify(receivableJobService, times(2)).markOverdue(anyLong());
    }

    @Test
    @DisplayName("单笔并发冲突（STATE_CONFLICT）不影响其余应收")
    void skipsConflictingReceivableAndContinues() {
        when(receivableJobService.findOverdueIds(any())).thenReturn(List.of(1L, 2L));
        when(receivableJobService.markOverdue(anyLong()))
                .thenThrow(new BizException(ErrorCode.STATE_CONFLICT))
                .thenReturn(1);

        assertThat(job.runOnce()).isEqualTo(1);
    }

    @Test
    @DisplayName("没有逾期应收时不动作")
    void noopWhenNothingOverdue() {
        when(receivableJobService.findOverdueIds(any())).thenReturn(List.of());

        assertThat(job.runOnce()).isZero();
        verify(receivableJobService, never()).markOverdue(anyLong());
    }
}
