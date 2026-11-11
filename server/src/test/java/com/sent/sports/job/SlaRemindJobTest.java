package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.mapper.po.CustomOrderPO;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import com.sent.sports.refund.mapper.po.RefundOrderPO;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/** SLA 升级提醒任务单测（P5-47）：两路待办汇总、单路异常跳过不中断整轮、无数据返回 0。 */
@ExtendWith(MockitoExtension.class)
class SlaRemindJobTest {

    @Mock
    private CustomOrderMapper customOrderMapper;
    @Mock
    private RefundOrderMapper refundOrderMapper;

    private SlaRemindJob job;

    @BeforeEach
    void setUp() {
        job = new SlaRemindJob(customOrderMapper, refundOrderMapper,
                Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("汇总审图超时与待审退款两路待办总数")
    void sumsBothSlaBacklogs() {
        when(customOrderMapper.selectReviewTimeout(any(), anyInt()))
                .thenReturn(List.of(new CustomOrderPO(), new CustomOrderPO()));
        when(refundOrderMapper.selectPending(anyInt()))
                .thenReturn(List.of(new RefundOrderPO()));

        assertThat(job.runOnce()).isEqualTo(3);
    }

    @Test
    @DisplayName("审图超时查询抛业务异常时跳过该路，仍汇总待审退款")
    void skipsFailingQueryAndContinues() {
        when(customOrderMapper.selectReviewTimeout(any(), anyInt()))
                .thenThrow(new BizException(ErrorCode.STATE_CONFLICT));
        when(refundOrderMapper.selectPending(anyInt()))
                .thenReturn(List.of(new RefundOrderPO(), new RefundOrderPO()));

        assertThat(job.runOnce()).isEqualTo(2);
    }

    @Test
    @DisplayName("两路都没有待办时返回 0")
    void noopWhenNoBacklog() {
        when(customOrderMapper.selectReviewTimeout(any(), anyInt())).thenReturn(List.of());
        when(refundOrderMapper.selectPending(anyInt())).thenReturn(List.of());

        assertThat(job.runOnce()).isZero();
    }
}
