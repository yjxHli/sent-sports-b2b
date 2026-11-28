package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.finance.service.ContractJobService;
import com.sent.sports.finance.service.dto.ContractReminder;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 合同到期提醒：三级窗口只提醒一次，且只有条件更新成功才投站内信。 */
@ExtendWith(MockitoExtension.class)
class ContractRemindJobTest {

    @Mock
    private ContractJobService contractJobService;
    @Mock
    private MessageSendService messageSendService;

    private ContractRemindJob job;

    @BeforeEach
    void setUp() {
        job = new ContractRemindJob(contractJobService, messageSendService,
                Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneOffset.UTC));
    }

    /** 递增档位：1=30 天、2=7 天、3=到期当天。 */
    private static ContractReminder candidate(long id, int currentLevel, int remindLevel,
                                               LocalDate expireDate) {
        return new ContractReminder(id, 9002L, "CT-1", 150000L, expireDate, currentLevel, remindLevel);
    }

    @Test
    @DisplayName("30 天内到期：条件更新成功后发 SYSTEM 站内信，含合同号与到期日")
    void notifiesThirtyDayWindow() {
        when(contractJobService.findExpiring(any())).thenReturn(List.of(
                candidate(1L, 0, 1, LocalDate.of(2026, 11, 24))));
        when(contractJobService.markReminded(1L, 1)).thenReturn(1);

        assertThat(job.runOnce()).isEqualTo(1);

        ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
        verify(messageSendService).send(captor.capture());
        InboxMessage msg = captor.getValue();
        assertThat(msg.customerId()).isEqualTo(9002L);
        assertThat(msg.msgType()).isEqualTo("SYSTEM");
        assertThat(msg.title()).isEqualTo("合同到期提醒");
        assertThat(msg.content()).contains("CT-1", "2026-11-24", "20 天");
        assertThat(msg.bizNo()).isEqualTo("CT-1");
    }

    @Test
    @DisplayName("7 天档与到期当天各提醒一次，内容按剩余天数区分")
    void notifiesSevenDayAndDueDateWindows() {
        when(contractJobService.findExpiring(any())).thenReturn(List.of(
                candidate(1L, 1, 2, LocalDate.of(2026, 11, 9)),
                candidate(2L, 2, 3, LocalDate.of(2026, 11, 4))));
        when(contractJobService.markReminded(anyLong(), anyInt())).thenReturn(1);

        assertThat(job.runOnce()).isEqualTo(2);

        ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
        verify(messageSendService, times(2)).send(captor.capture());
        assertThat(captor.getAllValues().get(0).content()).contains("5 天");
        assertThat(captor.getAllValues().get(1).content()).contains("今天到期");
    }

    @Test
    @DisplayName("并发抢先标记（影响 0 行）或业务冲突都不重复发信，且不影响其余合同")
    void skipsWhenMarkFails() {
        when(contractJobService.findExpiring(any())).thenReturn(List.of(
                candidate(1L, 0, 1, LocalDate.of(2026, 11, 24)),
                candidate(2L, 0, 1, LocalDate.of(2026, 11, 25))));
        when(contractJobService.markReminded(1L, 1)).thenReturn(0);
        when(contractJobService.markReminded(2L, 1))
                .thenThrow(new BizException(ErrorCode.STATE_CONFLICT));

        assertThat(job.runOnce()).isZero();
        verify(messageSendService, never()).send(any());
    }

    @Test
    @DisplayName("没有待提醒合同时不动作")
    void noopWhenNoContracts() {
        when(contractJobService.findExpiring(any())).thenReturn(List.of());

        assertThat(job.runOnce()).isZero();
        verify(contractJobService, never()).markReminded(anyLong(), anyInt());
        verify(messageSendService, never()).send(any());
    }
}
