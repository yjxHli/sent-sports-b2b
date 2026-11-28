package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.service.ReceivableJobService;
import com.sent.sports.finance.service.dto.OverdueReceivable;
import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.notify.service.dto.InboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 应收逾期重算任务单测：逐笔更新状态、并发冲突跳过；2026-11-27 起成功置 OVERDUE 时发站内信。 */
@ExtendWith(MockitoExtension.class)
class OverdueJobTest {

    @Mock
    private ReceivableJobService receivableJobService;
    @Mock
    private MessageSendService messageSendService;

    private OverdueJob job;

    @BeforeEach
    void setUp() {
        job = new OverdueJob(receivableJobService, messageSendService,
                Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneOffset.UTC));
    }

    private OverdueReceivable candidate(long id) {
        return new OverdueReceivable(id, 9002L, 150000L, LocalDate.of(2026, 11, 1), "SO-1");
    }

    @Test
    @DisplayName("逾期应收批量置 OVERDUE，每笔成功都发一条 SYSTEM 站内信")
    void marksOverdueReceivablesAndNotifies() {
        when(receivableJobService.findOverdueDetails(any())).thenReturn(List.of(candidate(1L), candidate(2L)));
        when(receivableJobService.markOverdue(anyLong())).thenReturn(1);

        assertThat(job.runOnce()).isEqualTo(2);
        verify(receivableJobService, times(2)).markOverdue(anyLong());

        ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
        verify(messageSendService, times(2)).send(captor.capture());
        InboxMessage first = captor.getAllValues().get(0);
        assertThat(first.customerId()).isEqualTo(9002L);
        assertThat(first.msgType()).isEqualTo("SYSTEM");
        assertThat(first.title()).isEqualTo("应收逾期提醒");
        assertThat(first.content()).contains("SO-1", "1,500.00", "2026-11-01");
    }

    @Test
    @DisplayName("单笔并发冲突（STATE_CONFLICT）跳过通知，不影响其余应收")
    void skipsConflictingReceivableAndContinues() {
        when(receivableJobService.findOverdueDetails(any())).thenReturn(List.of(candidate(1L), candidate(2L)));
        when(receivableJobService.markOverdue(1L))
                .thenThrow(new BizException(ErrorCode.STATE_CONFLICT));
        when(receivableJobService.markOverdue(2L)).thenReturn(1);

        assertThat(job.runOnce()).isEqualTo(1);
        verify(messageSendService, times(1)).send(any());
    }

    @Test
    @DisplayName("markOverdue 影响 0 行（已被其他事务处理）不发重复通知")
    void noNotificationWhenAlreadyMarked() {
        when(receivableJobService.findOverdueDetails(any())).thenReturn(List.of(candidate(1L)));
        when(receivableJobService.markOverdue(1L)).thenReturn(0);

        assertThat(job.runOnce()).isZero();
        verify(messageSendService, never()).send(any());
    }

    @Test
    @DisplayName("没有逾期应收时不动作")
    void noopWhenNothingOverdue() {
        when(receivableJobService.findOverdueDetails(any())).thenReturn(List.of());

        assertThat(job.runOnce()).isZero();
        verify(receivableJobService, never()).markOverdue(anyLong());
        verify(messageSendService, never()).send(any());
    }
}
