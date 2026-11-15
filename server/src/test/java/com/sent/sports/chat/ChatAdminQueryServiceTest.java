package com.sent.sports.chat;

import com.sent.sports.chat.mapper.ChatSessionMapper;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.service.dto.TodayStatsResult;
import com.sent.sports.chat.service.impl.ChatAdminQueryServiceImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 工作台「今日完成接待数」查询：按本地自然日统计该客服已结束的会话。
 *
 * <p>口径：{@code chat_session.status = 'CLOSED' AND staff_id = ? AND ended_at ∈ [今天 00:00, 明天 00:00)}。
 * 区间右开，跨零点自动换日——这就是「每天刷新」的实现方式，不需要定时任务清零。</p>
 */
class ChatAdminQueryServiceTest extends BaseUnitTest {

    /** 固定在北京时间 2026-11-13 20:30，便于断言日界。 */
    private static final Clock NOW_CLOCK = Clock.fixed(
            Instant.parse("2026-11-13T12:30:00Z"), ZoneId.of("Asia/Shanghai"));

    @Mock
    private ChatSessionMapper chatSessionMapper;
    @Mock
    private AgentPresenceRepository presenceRepository;
    @Mock
    private Clock clock;

    @InjectMocks
    private ChatAdminQueryServiceImpl queryService;

    @Test
    void todayStatsCountsClosedSessionsWithinLocalDay() {
        when(clock.instant()).thenReturn(NOW_CLOCK.instant());
        when(clock.getZone()).thenReturn(NOW_CLOCK.getZone());
        when(chatSessionMapper.countClosedByStaffBetween(eq(9001L), any(), any())).thenReturn(4);

        TodayStatsResult result = queryService.todayStats(9001L);

        assertEquals(9001L, result.staffId());
        assertEquals(4, result.closedCount());
        assertEquals("2026-11-13", result.date());

        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> to = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(chatSessionMapper).countClosedByStaffBetween(eq(9001L), from.capture(), to.capture());
        assertEquals(LocalDate.of(2026, 11, 13).atStartOfDay(), from.getValue(), "起点必须是当天 00:00");
        assertEquals(LocalDate.of(2026, 11, 14).atStartOfDay(), to.getValue(), "终点必须是次日 00:00（右开）");
    }

    /** 跨零点：时钟走到次日凌晨，日期与统计区间必须一起换掉，否则「今日」会永远停在昨天。 */
    @Test
    void todayStatsRollsOverAtMidnight() {
        Clock nextDay = Clock.fixed(Instant.parse("2026-11-13T16:30:00Z"), ZoneId.of("Asia/Shanghai"));
        when(clock.instant()).thenReturn(nextDay.instant());
        when(clock.getZone()).thenReturn(nextDay.getZone());
        when(chatSessionMapper.countClosedByStaffBetween(eq(9001L), any(), any())).thenReturn(0);

        TodayStatsResult result = queryService.todayStats(9001L);

        assertEquals("2026-11-14", result.date());
        ArgumentCaptor<LocalDateTime> from = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(chatSessionMapper).countClosedByStaffBetween(eq(9001L), from.capture(), any());
        assertEquals(LocalDate.of(2026, 11, 14).atStartOfDay(), from.getValue());
    }
}
