package com.sent.sports.chat;

import com.sent.sports.chat.mapper.ChatAuditMapper;
import com.sent.sports.chat.mapper.ChatMetricsMapper;
import com.sent.sports.chat.mapper.ChatSatisfactionMapper;
import com.sent.sports.chat.mapper.ChatSessionMapper;
import com.sent.sports.chat.mapper.ChatSessionNoteMapper;
import com.sent.sports.chat.mapper.po.ChatMetricsRowPO;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.service.ChatQueryService;
import com.sent.sports.chat.service.impl.ChatAdminQueryServiceImpl;
import com.sent.sports.support.BaseUnitTest;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 客服指标（2026-11-18 用户要求）：按时间区间聚合每位客服的接待量、满意度、解决率。
 *
 * <p>口径与工作台「今日满意率」一致：满意率 = SATISFIED ÷ 有效评价，无评价为 -1（前端 --）；
 * 解决率 = 小结 result=DONE 的会话 ÷ 已结束会话，无会话为 -1。
 * 区间统一半开 [from, to)，保证「近 7 天」「自定义时间」边界不重不漏。</p>
 */
class ChatMetricsQueryTest extends BaseUnitTest {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 11, 1, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 11, 18, 0, 0);

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
    void metricsAggregatesEachStaffAndComputesRates() {
        when(metricsMapper.selectMetrics(eq(FROM), eq(TO), eq(null))).thenReturn(List.of(
                po(9001L, "客服甲", 10, 8, 6, 7, 300),
                po(9002L, "客服乙", 5, 0, 0, 0, 0)));

        var result = queryService.metrics(FROM, TO, null);

        assertEquals(2, result.rows().size());
        assertEquals("2026-11-01", result.from(), "区间起点即用户所选起日");
        assertEquals("2026-11-17", result.to(), "传入次日 00:00，回显应为统计止日 11-17，不能少一天");
        var first = result.rows().get(0);
        assertEquals(9001L, first.staffId());
        assertEquals("客服甲", first.staffName());
        assertEquals(10, first.closedCount());
        assertEquals(8, first.ratedCount(), "一般/不满意也算有效评价");
        assertEquals(6, first.satisfiedCount());
        assertEquals(75, first.satisfactionRate(), "满意 6 / 有效 8 = 75%");
        assertEquals(70, first.resolutionRate(), "解决 7 / 接待 10 = 70%");
        assertEquals(300L, first.avgDurationSeconds());
        var second = result.rows().get(1);
        assertEquals(-1, second.satisfactionRate(), "无评价显示 --，不能误显示 0%");
        assertEquals(0, second.resolutionRate(), "有接待但没填小结 = 0%，与无接待不同");
    }

    @Test
    void metricsPassesRangeAndStaffFilterToMapper() {
        when(metricsMapper.selectMetrics(any(), any(), any())).thenReturn(List.of());

        var result = queryService.metrics(FROM, TO, 9003L);

        verify(metricsMapper).selectMetrics(FROM, TO, 9003L);
        assertEquals(0, result.rows().size());
    }

    @Test
    void metricsWithoutAnyClosedSessionShowsDashForBothRates() {
        when(metricsMapper.selectMetrics(any(), any(), any())).thenReturn(List.of(
                po(9003L, "客服丙", 0, 0, 0, 0, 0)));

        var row = queryService.metrics(FROM, TO, null).rows().get(0);

        assertEquals(-1, row.satisfactionRate());
        assertEquals(-1, row.resolutionRate(), "零接待时解决率也应显示 --");
        assertEquals(0L, row.avgDurationSeconds());
    }

    private static ChatMetricsRowPO po(long staffId, String name, int closed, int rated,
                                       int satisfied, int resolved, long avgSeconds) {
        ChatMetricsRowPO po = new ChatMetricsRowPO();
        po.setStaffId(staffId);
        po.setStaffName(name);
        po.setClosedCount(closed);
        po.setRatedCount(rated);
        po.setSatisfiedCount(satisfied);
        po.setResolvedCount(resolved);
        po.setAvgDurationSeconds(avgSeconds);
        return po;
    }
}
