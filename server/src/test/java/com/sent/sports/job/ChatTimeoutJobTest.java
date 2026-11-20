package com.sent.sports.job;

import com.sent.sports.chat.service.ChatJobQueryService;
import com.sent.sports.chat.service.CloseSessionUseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
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

/** 会话超时结束任务单测（P5-45）：逐个关闭、并发冲突会话跳过不中断整轮。 */
@ExtendWith(MockitoExtension.class)
class ChatTimeoutJobTest {

    private static final Instant NOW = Instant.parse("2026-11-04T02:00:00Z");

    @Mock
    private ChatJobQueryService chatJobQueryService;
    @Mock
    private CloseSessionUseCase closeSessionUseCase;

    private ChatTimeoutJob job;

    @BeforeEach
    void setUp() {
        job = new ChatTimeoutJob(chatJobQueryService, closeSessionUseCase,
                Clock.fixed(NOW, ZoneOffset.UTC), ChatTimeoutJob.DEFAULT_IDLE_MINUTES);
    }

    /**
     * 业务规则（2026-11-16 用户要求）：客户超过 10 分钟没发消息自动结束会话。
     * <p>阈值是整条链路的唯一真源，扫描边界必须是「当前时间 - 10 分钟」——写成 30 分钟
     * 就会出现「客户早走了、会话还挂在进行中占着坐席名额」，写成 1 分钟又会把正在对话的会话掐掉。</p>
     */
    @Test
    @DisplayName("空闲阈值 10 分钟：扫描边界 = 当前时间 - 10 分钟")
    void scansSessionsIdleBeyondTenMinutes() {
        ArgumentCaptor<LocalDateTime> beforeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        when(chatJobQueryService.findIdleSessionIds(beforeCaptor.capture(), anyInt())).thenReturn(List.of());

        job.runOnce();

        LocalDateTime expected = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMinutes(10);
        assertThat(beforeCaptor.getValue()).isEqualTo(expected);
        assertThat(ChatTimeoutJob.DEFAULT_IDLE_MINUTES).isEqualTo(10);
    }

    @Test
    @DisplayName("批量关闭空闲超过 10 分钟的会话（落库走用例，坐席名额同步释放）")
    void closesAllIdleSessions() {
        when(chatJobQueryService.findIdleSessionIds(any(), anyInt()))
                .thenReturn(List.of(1L, 2L));
        when(closeSessionUseCase.timeoutClose(anyLong())).thenReturn(true);

        assertThat(job.runOnce()).isEqualTo(2);

        verify(closeSessionUseCase, times(2)).timeoutClose(anyLong());
        verify(closeSessionUseCase).timeoutClose(1L);
        verify(closeSessionUseCase).timeoutClose(2L);
    }

    @Test
    @DisplayName("单个会话已被并发关闭（STATE_CONFLICT）不影响其余会话")
    void skipsConflictingSessionAndContinues() {
        when(chatJobQueryService.findIdleSessionIds(any(), anyInt()))
                .thenReturn(List.of(1L, 2L));
        when(closeSessionUseCase.timeoutClose(anyLong()))
                .thenThrow(new BizException(ErrorCode.STATE_CONFLICT))
                .thenReturn(true);

        assertThat(job.runOnce()).isEqualTo(1);
    }

    @Test
    @DisplayName("已被别人结束的会话按跳过处理，不计数")
    void alreadyClosedSessionIsSkipped() {
        when(chatJobQueryService.findIdleSessionIds(any(), anyInt()))
                .thenReturn(List.of(1L));

        // timeoutClose 对已结束会话返回 false（不抛异常），任务不能把它算成关闭成功
        assertThat(job.runOnce()).isZero();
        verify(closeSessionUseCase, never()).timeoutClose(2L);
    }

    /** 阈值走配置（{@code app.chat.idle-minutes}），运维调参不用改代码。 */
    @Test
    @DisplayName("阈值可配置：构造传入的分钟数就是扫描边界")
    void idleThresholdIsConfigurable() {
        ChatTimeoutJob custom = new ChatTimeoutJob(chatJobQueryService, closeSessionUseCase,
                Clock.fixed(NOW, ZoneOffset.UTC), 3);
        ArgumentCaptor<LocalDateTime> beforeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        when(chatJobQueryService.findIdleSessionIds(beforeCaptor.capture(), anyInt())).thenReturn(List.of());

        custom.runOnce();

        assertThat(beforeCaptor.getValue())
                .isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMinutes(3));
    }

    @Test
    @DisplayName("没有空闲会话时不动作")
    void noopWhenNothingIdle() {
        when(chatJobQueryService.findIdleSessionIds(any(), anyInt())).thenReturn(List.of());

        assertThat(job.runOnce()).isZero();
        verify(closeSessionUseCase, never()).timeoutClose(anyLong());
    }

}
