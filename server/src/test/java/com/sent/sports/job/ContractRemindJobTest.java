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
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** 合同到期提醒任务单测（P5-43）：只读统计 30 天内到期合同数、异常跳过本轮、无数据返回 0。 */
@ExtendWith(MockitoExtension.class)
class ContractRemindJobTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ContractRemindJob job;

    @BeforeEach
    void setUp() {
        job = new ContractRemindJob(jdbcTemplate,
                Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("统计到 N 份 30 天内到期合同")
    void countsExpiringContracts() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), ArgumentMatchers.<Object>any()))
                .thenReturn(3);

        assertThat(job.runOnce()).isEqualTo(3);
    }

    @Test
    @DisplayName("统计查询抛业务异常时跳过本轮")
    void skipsRoundOnBizException() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), ArgumentMatchers.<Object>any()))
                .thenThrow(new BizException(ErrorCode.SYSTEM_ERROR));

        assertThat(job.runOnce()).isZero();
    }

    @Test
    @DisplayName("没有临近到期合同时返回 0")
    void noopWhenNothingExpiring() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), ArgumentMatchers.<Object>any()))
                .thenReturn(0);

        assertThat(job.runOnce()).isZero();
    }
}
