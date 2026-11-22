package com.sent.sports.chat;

import com.sent.sports.chat.mapper.ChatAuditMapper;
import com.sent.sports.chat.mapper.ChatMetricsMapper;
import com.sent.sports.chat.mapper.ChatSatisfactionMapper;
import com.sent.sports.chat.mapper.ChatSessionMapper;
import com.sent.sports.chat.mapper.ChatSessionNoteMapper;
import com.sent.sports.chat.mapper.po.ChatHistoryRowPO;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.service.ChatQueryService;
import com.sent.sports.chat.service.dto.ChatAuditDtos;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
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
    private ChatSatisfactionMapper satisfactionMapper;
    @Mock
    private Clock clock;
    @Mock
    private ChatAuditMapper auditMapper;
    @Mock
    private ChatSessionNoteMapper noteMapper;
    @Mock
    private ChatQueryService chatQueryService;
    @Mock
    private ChatMetricsMapper metricsMapper;

    @InjectMocks
    private ChatAdminQueryServiceImpl queryService;

    @Test
    void todayStatsCountsClosedSessionsWithinLocalDay() {
        when(clock.instant()).thenReturn(NOW_CLOCK.instant());
        when(clock.getZone()).thenReturn(NOW_CLOCK.getZone());
        when(chatSessionMapper.countClosedByStaffBetween(eq(9001L), any(), any())).thenReturn(4);
        when(satisfactionMapper.countRatedBetween(eq(9001L), any(), any())).thenReturn(4);
        when(satisfactionMapper.countSatisfiedBetween(eq(9001L), any(), any())).thenReturn(3);

        TodayStatsResult result = queryService.todayStats(9001L);

        assertEquals(9001L, result.staffId());
        assertEquals(4, result.closedCount());
        assertEquals("2026-11-13", result.date());
        assertEquals(4, result.ratedCount(), "一般/不满意也算有效评价");
        assertEquals(3, result.satisfiedCount());
        assertEquals(75, result.ratePercent(), "满意率 = 满意 3 / 有效 4 x 100 = 75%");

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

    @Test
    void todayStatsWithoutRatingShowsDashInsteadOfZero() {
        when(clock.instant()).thenReturn(NOW_CLOCK.instant());
        when(clock.getZone()).thenReturn(NOW_CLOCK.getZone());

        TodayStatsResult result = queryService.todayStats(9001L);

        assertEquals(-1, result.ratePercent(), "无有效评价时前端显示 --，不能误显示 0%");
    }

    /**
     * 用户要求「最多可看 10 次客服聊天记录」：limit 是服务端硬上限，
     * 前端传 100 也只允许 10，防止一次把客户全部历史拉走（越权 + 性能）。
     */
    @Test
    void customerHistoryIsCappedAtTenOnServerSide() {
        when(auditMapper.selectHistory(anyLong(), anyLong(), anyInt())).thenReturn(java.util.List.of());

        queryService.customerHistory(9002L, 0L, 100);

        verify(auditMapper).selectHistory(9002L, 0L, 10);
    }

    @Test
    void customerHistoryKeepsSmallerLimitAndExcludesCurrentSession() {
        when(auditMapper.selectHistory(anyLong(), anyLong(), anyInt())).thenReturn(java.util.List.of(
                historyRow(88L), historyRow(87L)));

        java.util.List<ChatAuditDtos.HistoryRow> rows = queryService.customerHistory(9002L, 99L, 2);

        verify(auditMapper).selectHistory(9002L, 99L, 2);
        assertEquals(2, rows.size());
        assertEquals(88L, rows.get(0).sessionId());
    }

    /** 小结是否存在要看「问题概述」，不能看处理结果：未解决也是填过小结的。 */
    @Test
    void auditMarksNoteFilledWhenResultIsUnresolved() {
        com.sent.sports.chat.mapper.po.ChatAuditRowPO po =
                new com.sent.sports.chat.mapper.po.ChatAuditRowPO();
        po.setSessionId(7L);
        po.setCustomerId(9002L);
        po.setStaffId(9001L);
        po.setSummary("客户咨询加急");
        po.setResult("UNRESOLVED");
        when(auditMapper.selectAuditPage(any(), any(), any(), any(), any(), any())).thenReturn(pageOf(po));
        when(auditMapper.countAudit(any(), any(), any(), any(), any())).thenReturn(1L);

        var result = queryService.audit(new com.sent.sports.common.web.PageQuery(), null,
                null, null, null, null);

        assertEquals(1, result.getTotal());
        assertEquals("已填写", result.getList().get(0).noteStatus());
        assertEquals("UNRESOLVED", result.getList().get(0).result());
    }

    @Test
    void openSessionsHidesQueueWhenStaffOffline() {
        // 2026-11-21 用户要求：离线状态「看不到有多少人排队」——不能只在页面上 v-if，
        // 接口层就不返回排队行，换任何客户端都看不到。
        when(presenceRepository.findByStaffId(9001L)).thenReturn(java.util.Optional.of(
                new com.sent.sports.chat.domain.AgentPresence(9001L,
                        com.sent.sports.chat.domain.PresenceStatus.OFFLINE, 0, 3, 0L)));
        when(chatSessionMapper.selectVisibleActiveByStaff(anyLong())).thenReturn(java.util.List.of());

        var result = queryService.openSessions(9001L);

        assertEquals(0, result.waiting().size(), "离线坐席不应看到排队客户");
    }

    @Test
    void openSessionsShowsQueueWhenStaffOnlineOrBusy() {
        when(presenceRepository.findByStaffId(9001L)).thenReturn(java.util.Optional.of(
                new com.sent.sports.chat.domain.AgentPresence(9001L,
                        com.sent.sports.chat.domain.PresenceStatus.BUSY, 0, 3, 0L)));
        when(chatSessionMapper.selectVisibleActiveByStaff(anyLong())).thenReturn(java.util.List.of());
        when(chatSessionMapper.selectVisibleWaitingFifo(anyInt())).thenReturn(java.util.List.of(waitingPo(11L)));

        var result = queryService.openSessions(9001L);

        assertEquals(1, result.waiting().size(), "忙碌只是不自动接入，队列仍要可见");
        assertEquals(1, result.waiting().get(0).queuePosition(), "排队位置从 1 开始");
    }

    private static com.sent.sports.chat.mapper.po.ChatSessionPO waitingPo(long sessionId) {
        var po = new com.sent.sports.chat.mapper.po.ChatSessionPO();
        po.setId(sessionId);
        po.setCustomerId(9002L);
        po.setStatus("WAITING");
        po.setCreatedAt(LocalDateTime.of(2026, 11, 20, 10, 0));
        return po;
    }

    private static <T> com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> pageOf(T row) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        page.setRecords(java.util.List.of(row));
        page.setTotal(1);
        return page;
    }

    private static ChatHistoryRowPO historyRow(long sessionId) {
        ChatHistoryRowPO row = new ChatHistoryRowPO();
        row.setSessionId(sessionId);
        row.setStaffId(9001L);
        row.setStaffName("客服甲");
        row.setResult("DONE");
        row.setMessageCount(4);
        row.setStartedAt(LocalDateTime.of(2026, 11, 1, 10, 0));
        row.setEndedAt(LocalDateTime.of(2026, 11, 1, 10, 5));
        return row;
    }
}
