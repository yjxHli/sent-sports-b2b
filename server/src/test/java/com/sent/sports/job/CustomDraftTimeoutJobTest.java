package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customize.service.CustomDraftTimeoutQueryService;
import com.sent.sports.customize.service.CustomDraftTimeoutUseCase;
import com.sent.sports.customize.service.dto.CustomDraftWaiting;
import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.notify.service.dto.InboxMessage;
import com.sent.sports.order.service.CloseOrderUseCase;
import com.sent.sports.order.service.OrderQueryService;
import com.sent.sports.order.service.dto.CloseOrderCmd;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 定制图稿上传超时任务单测（2026-11-23 用户口径）：
 * 支付后 72 小时未上传 → 整单取消退款；期间每 12 小时站内信提醒一次并告知剩余时间。
 */
@ExtendWith(MockitoExtension.class)
class CustomDraftTimeoutJobTest {

    /** 固定「当前时刻」，避免用例依赖真实时钟。 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 12, 0);
    /** 存量豁免线：规则上线时刻，早于它支付的单不追溯取消。 */
    private static final String ENFORCE_FROM = "2026-11-23T00:00:00";

    @Mock
    private CustomDraftTimeoutQueryService queryService;
    @Mock
    private CustomDraftTimeoutUseCase timeoutUseCase;
    @Mock
    private CloseOrderUseCase closeOrderUseCase;
    @Mock
    private OrderQueryService orderQueryService;
    @Mock
    private MessageSendService messageSendService;

    private CustomDraftTimeoutJob job;

    @BeforeEach
    void setUp() {
        // Clock 的时区必须与 NOW 一致，否则 LocalDateTime.now(clock) 会按 UTC 解析，整体偏 8 小时
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        job = new CustomDraftTimeoutJob(queryService, timeoutUseCase, closeOrderUseCase,
                orderQueryService, messageSendService, clock, ENFORCE_FROM);
    }

    private CustomDraftWaiting waiting(LocalDateTime paidAt, LocalDateTime lastRemindAt) {
        return new CustomDraftWaiting("CU20261120000001", "SO20261120000001", 9002L, paidAt, lastRemindAt == null ? 0 : 1, lastRemindAt);
    }

    @Test
    @DisplayName("支付满 12 小时未超时：发一条站内信，文案带准确剩余时间")
    void remindsAfterTwelveHoursWithRemainingTime() {
        when(queryService.findWaitingDrafts(any(), anyInt()))
                .thenReturn(List.of(waiting(NOW.minusHours(12), null)));
        when(timeoutUseCase.claimReminder("CU20261120000001", NOW)).thenReturn(true);

        CustomDraftTimeoutJob.RunResult result = job.runOnce();

        assertThat(result.reminded()).isEqualTo(1);
        assertThat(result.cancelled()).isZero();
        ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
        verify(messageSendService).send(captor.capture());
        InboxMessage message = captor.getValue();
        assertThat(message.customerId()).isEqualTo(9002L);
        assertThat(message.title()).contains("定制图稿");
        // 支付后 12h → 距 72h 截止还剩 60h = 2 天 12 小时
        assertThat(message.content()).contains("剩余 2 天 12 小时");
        assertThat(message.bizNo()).isEqualTo("CU20261120000001");
        verify(closeOrderUseCase, never()).close(any());
    }

    @Test
    @DisplayName("支付未满 12 小时：不打扰，不发提醒")
    void staysQuietBeforeFirstReminderWindow() {
        when(queryService.findWaitingDrafts(any(), anyInt()))
                .thenReturn(List.of(waiting(NOW.minusHours(5), null)));

        CustomDraftTimeoutJob.RunResult result = job.runOnce();

        assertThat(result.reminded()).isZero();
        verify(messageSendService, never()).send(any());
        verify(timeoutUseCase, never()).claimReminder(anyString(), any());
    }

    @Test
    @DisplayName("距上次提醒不足 12 小时：本轮跳过（每 12 小时最多一次）")
    void doesNotRemindTwiceWithinTwelveHours() {
        when(queryService.findWaitingDrafts(any(), anyInt()))
                .thenReturn(List.of(waiting(NOW.minusHours(30), NOW.minusHours(6))));

        CustomDraftTimeoutJob.RunResult result = job.runOnce();

        assertThat(result.reminded()).isZero();
        verify(messageSendService, never()).send(any());
    }

    @Test
    @DisplayName("并发下没抢到提醒资格：不发信，避免重复推送")
    void skipsWhenReminderAlreadyClaimed() {
        when(queryService.findWaitingDrafts(any(), anyInt()))
                .thenReturn(List.of(waiting(NOW.minusHours(24), null)));
        when(timeoutUseCase.claimReminder(anyString(), any())).thenReturn(false);

        CustomDraftTimeoutJob.RunResult result = job.runOnce();

        assertThat(result.reminded()).isZero();
        verify(messageSendService, never()).send(any());
    }

    @Test
    @DisplayName("支付满 72 小时：整单关闭退款 + 定制单取消 + 取消通知")
    void cancelsAndRefundsAfterDeadline() {
        when(queryService.findWaitingDrafts(any(), anyInt()))
                .thenReturn(List.of(waiting(NOW.minusHours(73), NOW.minusHours(24))));
        when(timeoutUseCase.cancelOverdue("CU20261120000001")).thenReturn(true);

        CustomDraftTimeoutJob.RunResult result = job.runOnce();

        assertThat(result.cancelled()).isEqualTo(1);
        assertThat(result.reminded()).isZero();
        ArgumentCaptor<CloseOrderCmd> closeCaptor = ArgumentCaptor.forClass(CloseOrderCmd.class);
        verify(closeOrderUseCase).close(closeCaptor.capture());
        assertThat(closeCaptor.getValue().orderNo()).isEqualTo("SO20261120000001");
        assertThat(closeCaptor.getValue().reason()).contains("72 小时");
        verify(timeoutUseCase).cancelOverdue("CU20261120000001");
        ArgumentCaptor<InboxMessage> captor = ArgumentCaptor.forClass(InboxMessage.class);
        verify(messageSendService).send(captor.capture());
        assertThat(captor.getValue().title()).contains("自动取消");
        assertThat(captor.getValue().content()).contains("积分与使用的优惠券已返还");
    }

    @Test
    @DisplayName("订单已被并发关闭（STATE_CONFLICT）：仍把定制单补成取消，保证不挂在待上传列表")
    void stillCancelsCustomOrderWhenOrderAlreadyClosed() {
        when(queryService.findWaitingDrafts(any(), anyInt()))
                .thenReturn(List.of(waiting(NOW.minusHours(80), null)));
        doThrow(new BizException(ErrorCode.STATE_CONFLICT))
                .when(closeOrderUseCase).close(any());
        when(orderQueryService.isClosedWithFullRefund("SO20261120000001")).thenReturn(true);
        when(timeoutUseCase.cancelOverdue("CU20261120000001")).thenReturn(true);

        CustomDraftTimeoutJob.RunResult result = job.runOnce();

        assertThat(result.cancelled()).isEqualTo(1);
        verify(timeoutUseCase).cancelOverdue("CU20261120000001");
    }

    @Test
    @DisplayName("状态冲突但订单并未全额退款（已完成/售后中）：不取消定制单，绝不出现「单没了钱没退」")
    void keepsCustomOrderWhenOrderIsNotRefunded() {
        when(queryService.findWaitingDrafts(any(), anyInt()))
                .thenReturn(List.of(waiting(NOW.minusHours(80), null)));
        doThrow(new BizException(ErrorCode.STATE_CONFLICT))
                .when(closeOrderUseCase).close(any());
        when(orderQueryService.isClosedWithFullRefund("SO20261120000001")).thenReturn(false);

        CustomDraftTimeoutJob.RunResult result = job.runOnce();

        assertThat(result.cancelled()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(timeoutUseCase, never()).cancelOverdue(anyString());
        verify(messageSendService, never()).send(any());
    }

    @Test
    @DisplayName("退款失败（非状态冲突）：本轮不动定制单，留给下一轮重试")
    void keepsCustomOrderWhenRefundFails() {
        when(queryService.findWaitingDrafts(any(), anyInt()))
                .thenReturn(List.of(waiting(NOW.minusHours(80), null)));
        doThrow(new BizException(ErrorCode.BIZ_REJECT, "支付网关拒绝"))
                .when(closeOrderUseCase).close(any());

        CustomDraftTimeoutJob.RunResult result = job.runOnce();

        assertThat(result.cancelled()).isZero();
        assertThat(result.skipped()).isEqualTo(1);
        verify(timeoutUseCase, never()).cancelOverdue(anyString());
        verify(messageSendService, never()).send(any());
    }

    @Test
    @DisplayName("单条异常不中断整轮：后面的单照常处理")
    void oneFailureDoesNotStopTheRound() {
        CustomDraftWaiting bad = waiting(NOW.minusHours(80), null);
        CustomDraftWaiting good = new CustomDraftWaiting("CU20261120000002", "SO20261120000002", 9003L,
                NOW.minusHours(13), 0, null);
        when(queryService.findWaitingDrafts(any(), anyInt())).thenReturn(List.of(bad, good));
        doThrow(new IllegalStateException("boom")).when(closeOrderUseCase).close(any());
        when(timeoutUseCase.claimReminder("CU20261120000002", NOW)).thenReturn(true);

        CustomDraftTimeoutJob.RunResult result = job.runOnce();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.reminded()).isEqualTo(1);
        verify(messageSendService, times(1)).send(any());
    }

    @Test
    @DisplayName("扫描只取规则上线后支付的单：存量老单不追溯取消")
    void scansOnlyOrdersPaidAfterEnforceFrom() {
        when(queryService.findWaitingDrafts(any(), anyInt())).thenReturn(List.of());

        job.runOnce();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(queryService).findWaitingDrafts(captor.capture(), anyInt());
        assertThat(captor.getValue()).isEqualTo(LocalDateTime.parse(ENFORCE_FROM));
    }
}
