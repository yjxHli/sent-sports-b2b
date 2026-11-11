package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 坐席在线回收任务单测（P5-46）：返回受影响行数、异常跳过本轮、无命中返回 0。 */
@ExtendWith(MockitoExtension.class)
class PresenceReclaimJobTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PresenceReclaimJob job;

    @BeforeEach
    void setUp() {
        job = new PresenceReclaimJob(jdbcTemplate,
                Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("回收 N 个心跳超时的在线坐席")
    void reclaimsStalePresence() {
        when(jdbcTemplate.update(anyString(), ArgumentMatchers.<Object>any())).thenReturn(3);

        assertThat(job.runOnce()).isEqualTo(3);
    }

    @Test
    @DisplayName("更新抛业务异常时跳过本轮")
    void skipsRoundOnBizException() {
        when(jdbcTemplate.update(anyString(), ArgumentMatchers.<Object>any()))
                .thenThrow(new BizException(ErrorCode.SYSTEM_ERROR));

        assertThat(job.runOnce()).isZero();
    }

    @Test
    @DisplayName("心跳超时阈值可配置：120 秒时 cutoff 为 now-2min")
    void configurableTimeoutShiftsCutoff() {
        PresenceReclaimJob configured = new PresenceReclaimJob(jdbcTemplate,
                Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneOffset.UTC), 120_000L);
        when(jdbcTemplate.update(anyString(), ArgumentMatchers.<Object>any())).thenReturn(1);

        assertThat(configured.runOnce()).isEqualTo(1);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());
        assertThat(((Timestamp) captor.getValue()).toLocalDateTime())
                .isEqualTo(LocalDateTime.parse("2026-11-04T01:58:00"));
    }

    @Test
    @DisplayName("没有超时坐席时返回 0")
    void noopWhenNothingStale() {
        when(jdbcTemplate.update(anyString(), ArgumentMatchers.<Object>any())).thenReturn(0);

        assertThat(job.runOnce()).isZero();
    }
}
