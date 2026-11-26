package com.sent.sports.chat;

import com.sent.sports.chat.mapper.ChatAuditMapper;
import com.sent.sports.chat.mapper.ChatMetricsMapper;
import com.sent.sports.chat.mapper.ChatSatisfactionMapper;
import com.sent.sports.chat.mapper.ChatSessionMapper;
import com.sent.sports.chat.mapper.ChatSessionNoteMapper;
import com.sent.sports.chat.mapper.po.ChatHistoryRowPO;
import com.sent.sports.chat.mapper.po.ChatSessionNotePO;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.service.ChatQueryService;
import com.sent.sports.chat.service.dto.ChatAuditDtos;
import com.sent.sports.chat.service.dto.TodayStatsResult;
import com.sent.sports.chat.service.impl.ChatAdminQueryServiceImpl;
import com.sent.sports.customer.service.dto.CustomerContact;
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
    @Mock
    private com.sent.sports.customer.service.CustomerQueryService customerQueryService;

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

    /** 审查列表只收已提交工单：小结状态直接反映工单提交状态（V65）。 */
    @Test
    void auditMarksNoteSubmittedWhenResultIsUnresolved() {
        com.sent.sports.chat.mapper.po.ChatAuditRowPO po =
                new com.sent.sports.chat.mapper.po.ChatAuditRowPO();
        po.setSessionId(7L);
        po.setCustomerId(9002L);
        po.setStaffId(9001L);
        po.setSummary("客户咨询加急");
        po.setResult("UNRESOLVED");
        po.setSubmitStatus("SUBMITTED");
        when(auditMapper.selectAuditPage(any(), any(), any(), any(), any(), any())).thenReturn(pageOf(po));
        when(auditMapper.countAudit(any(), any(), any(), any(), any())).thenReturn(1L);

        var result = queryService.audit(new com.sent.sports.common.web.PageQuery(), null,
                null, null, null, null);

        assertEquals(1, result.getTotal());
        assertEquals("已提交", result.getList().get(0).noteStatus());
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

    @Test
    void openSessionsFillsCustomerNameAndAvatar() {
        // 2026-11-25 用户要求：左侧列表显示「进线用户的头像和姓名」，不再只显示「客户 9002」。
        // 客户资料属于 customer 模块，chat 只能经 CustomerQueryService 取（ArchUnit 规则）。
        when(presenceRepository.findByStaffId(9001L)).thenReturn(java.util.Optional.of(
                new com.sent.sports.chat.domain.AgentPresence(9001L,
                        com.sent.sports.chat.domain.PresenceStatus.ONLINE, 1, 3, 0L)));
        when(chatSessionMapper.selectVisibleActiveByStaff(9001L))
                .thenReturn(java.util.List.of(activePo(21L, 9002L)));
        when(customerQueryService.briefsOf(any())).thenReturn(java.util.Map.of(
                9002L, new com.sent.sports.customer.service.dto.CustomerBrief(9002L, "林小满", "f-avatar-1")));

        var result = queryService.openSessions(9001L);

        assertEquals("林小满", result.active().get(0).customerName());
        assertEquals("f-avatar-1", result.active().get(0).customerAvatarFileId());
    }

    @Test
    void openSessionsToleratesMissingCustomer() {
        // 客户被软删/查不到时不能 NPE，留给前端兜底成「客户 {id}」
        when(presenceRepository.findByStaffId(9001L)).thenReturn(java.util.Optional.of(
                new com.sent.sports.chat.domain.AgentPresence(9001L,
                        com.sent.sports.chat.domain.PresenceStatus.ONLINE, 1, 3, 0L)));
        when(chatSessionMapper.selectVisibleActiveByStaff(9001L))
                .thenReturn(java.util.List.of(activePo(22L, 9003L)));
        when(customerQueryService.briefsOf(any())).thenReturn(java.util.Map.of());

        var result = queryService.openSessions(9001L);

        assertEquals(9003L, result.active().get(0).customerId());
        org.junit.jupiter.api.Assertions.assertNull(result.active().get(0).customerName());
        org.junit.jupiter.api.Assertions.assertNull(result.active().get(0).customerAvatarFileId());
    }

    @Test
    void notePrefillsCustomerInfoBeforeTicketFilled() {
        // 客服第一次打开抽屉时工单还不存在，基础信息也必须已经带入，否则要客服手抄客户资料。
        com.sent.sports.chat.mapper.po.ChatSessionPO session = activePo(33L, 9002L);
        session.setStartedAt(LocalDateTime.of(2026, 11, 25, 9, 30));
        when(chatSessionMapper.selectById(33L)).thenReturn(session);
        when(chatQueryService.history(33L, 0)).thenReturn(java.util.List.of());
        when(noteMapper.selectBySessionId(33L)).thenReturn(null);
        when(customerQueryService.contactOf(9002L)).thenReturn(new CustomerContact(
                9002L, "林小满", "13800138000", "lin@sent.test"));

        ChatAuditDtos.NoteVO note = queryService.transcript(33L).note();

        org.junit.jupiter.api.Assertions.assertNotNull(note, "空工单也要返回预填基础信息");
        assertEquals(33L, note.sessionId());
        assertEquals("林小满", note.customerNickname());
        assertEquals("13800138000", note.customerPhone());
        org.junit.jupiter.api.Assertions.assertNull(note.summary(), "未填写时不能伪造问题描述");
        org.junit.jupiter.api.Assertions.assertEquals(0L, note.updatedAtMillis());
        assertEquals(LocalDateTime.of(2026, 11, 25, 9, 30)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                note.consultedAtMillis());
    }

    @Test
    void noteCarriesCustomerContactAndConsultedAt() {
        // 2026-11-25 服务工单：GET note 必须自动带回客户昵称/联系方式/咨询时间，不需要客服手填。
        com.sent.sports.chat.mapper.po.ChatSessionPO session = activePo(31L, 9002L);
        session.setStartedAt(LocalDateTime.of(2026, 11, 25, 9, 30));
        when(chatSessionMapper.selectById(31L)).thenReturn(session);
        when(chatQueryService.history(31L, 0)).thenReturn(java.util.List.of());
        when(customerQueryService.contactOf(9002L)).thenReturn(new CustomerContact(
                9002L, "林小满", "13800138000", "lin@sent.test"));

        ChatSessionNotePO po = new ChatSessionNotePO();
        po.setSessionId(31L);
        po.setCustomerId(9002L);
        po.setStaffId(9001L);
        po.setSummary("咨询定制足球印字");
        po.setCategory("INQUIRY");
        po.setIssueInfo("CLEAR");
        po.setResult("DONE");
        po.setSolution("已告知起订量");
        po.setSubCategory("印字Logo");
        po.setCustomerDemand("本周到货");
        po.setVerifiedInfo("订单 S001");
        po.setNeedFollowup(true);
        po.setFollowupTime(LocalDateTime.of(2026, 11, 26, 10, 0));
        po.setAttachmentsJson("[\"file-1\"]");
        when(noteMapper.selectBySessionId(31L)).thenReturn(po);

        ChatAuditDtos.NoteVO note = queryService.transcript(31L).note();

        assertEquals("林小满", note.customerNickname());
        assertEquals("13800138000", note.customerPhone());
        assertEquals("lin@sent.test", note.customerEmail());
        assertEquals(LocalDateTime.of(2026, 11, 25, 9, 30)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                note.consultedAtMillis(), "咨询时间取会话开始时间");
        assertEquals("印字Logo", note.subCategory());
        assertEquals("本周到货", note.customerDemand());
        assertEquals("订单 S001", note.verifiedInfo());
        assertEquals(true, note.needFollowup());
        assertEquals("[\"file-1\"]", note.attachmentsJson());
        verify(customerQueryService).contactOf(9002L);
    }

    @Test
    void sessionDetailFallsBackToCreatedAtWhenStartedAtMissing() {
        // 自动接入的会话可能没有 started_at；工单「咨询时间」必须回退到创建时间而不是 0。
        com.sent.sports.chat.mapper.po.ChatSessionPO session = activePo(32L, 9002L);
        session.setStartedAt(null);
        when(chatSessionMapper.selectById(32L)).thenReturn(session);

        ChatAuditDtos.SessionDetail detail = queryService.sessionDetail(32L);

        assertEquals(LocalDateTime.of(2026, 11, 25, 10, 0)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                detail.startedAtMillis());
    }

    private static com.sent.sports.chat.mapper.po.ChatSessionPO activePo(long sessionId, long customerId) {
        var po = new com.sent.sports.chat.mapper.po.ChatSessionPO();
        po.setId(sessionId);
        po.setCustomerId(customerId);
        po.setStaffId(9001L);
        po.setStatus("ACTIVE");
        po.setCreatedAt(LocalDateTime.of(2026, 11, 25, 10, 0));
        return po;
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
