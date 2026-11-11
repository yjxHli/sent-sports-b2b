package com.sent.sports.job;

import com.sent.sports.chat.mapper.ChatSessionMapper;
import com.sent.sports.chat.mapper.po.ChatSessionPO;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 会话超时结束任务单测（P5-45）：逐个条件关闭、并发冲突会话跳过不中断整轮。 */
@ExtendWith(MockitoExtension.class)
class ChatTimeoutJobTest {

    @Mock
    private ChatSessionMapper chatSessionMapper;

    private ChatTimeoutJob job;

    @BeforeEach
    void setUp() {
        job = new ChatTimeoutJob(chatSessionMapper,
                Clock.fixed(Instant.parse("2026-11-04T02:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    @DisplayName("批量关闭空闲超过 30 分钟的会话")
    void closesAllIdleSessions() {
        when(chatSessionMapper.selectIdleSessions(any(), anyInt()))
                .thenReturn(List.of(session(1L), session(2L)));
        when(chatSessionMapper.closeSession(anyLong(), anyString())).thenReturn(1);

        assertThat(job.runOnce()).isEqualTo(2);

        verify(chatSessionMapper, times(2)).closeSession(anyLong(), eq("TIMEOUT"));
    }

    @Test
    @DisplayName("单个会话已被并发关闭（STATE_CONFLICT）不影响其余会话")
    void skipsConflictingSessionAndContinues() {
        when(chatSessionMapper.selectIdleSessions(any(), anyInt()))
                .thenReturn(List.of(session(1L), session(2L)));
        when(chatSessionMapper.closeSession(anyLong(), anyString()))
                .thenThrow(new BizException(ErrorCode.STATE_CONFLICT))
                .thenReturn(1);

        assertThat(job.runOnce()).isEqualTo(1);
    }

    @Test
    @DisplayName("没有空闲会话时不动作")
    void noopWhenNothingIdle() {
        when(chatSessionMapper.selectIdleSessions(any(), anyInt())).thenReturn(List.of());

        assertThat(job.runOnce()).isZero();
        verify(chatSessionMapper, never()).closeSession(anyLong(), anyString());
    }

    private ChatSessionPO session(long id) {
        ChatSessionPO po = new ChatSessionPO();
        po.setId(id);
        return po;
    }
}
