package com.sent.sports.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.chat.domain.ChatSessionNote;
import com.sent.sports.chat.domain.PresenceStatus;
import com.sent.sports.chat.dto.admin.ChatMetricsDtos;
import com.sent.sports.chat.mapper.ChatAuditMapper;
import com.sent.sports.chat.mapper.ChatMetricsMapper;
import com.sent.sports.chat.mapper.ChatSatisfactionMapper;
import com.sent.sports.chat.mapper.ChatSessionMapper;
import com.sent.sports.chat.mapper.ChatSessionNoteMapper;
import com.sent.sports.chat.mapper.po.ChatAuditRowPO;
import com.sent.sports.chat.mapper.po.ChatAuditStaffPO;
import com.sent.sports.chat.mapper.po.ChatHistoryRowPO;
import com.sent.sports.chat.mapper.po.ChatSessionNotePO;
import com.sent.sports.chat.mapper.po.ChatSessionPO;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.service.ChatAdminQueryService;
import com.sent.sports.chat.service.ChatQueryService;
import com.sent.sports.chat.service.dto.ChatAuditDtos;
import com.sent.sports.chat.service.dto.OpenSessionsResult;
import com.sent.sports.chat.service.dto.TodayStatsResult;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.customer.service.CustomerQueryService;
import com.sent.sports.customer.service.dto.CustomerBrief;
import com.sent.sports.customer.service.dto.CustomerContact;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * 客服工作台查询（P4-50 空桩 → 收口阶段真实查询）。
 *
 * <p>会话列表直接读 {@code chat_session}，按主键倒序（最新会话优先），供坐席「接待」；
 * 分页交给 MyBatis-Plus 的 {@link Page}。</p>
 * <p>工作台改成「进行中 / 排队中」两栏 + 多 Tab 后，左侧列表走 {@link #openSessions(long)}：
 * 进行中只取该客服的 ACTIVE，排队中按创建时间先来后到并带 1 开始的排队位置。</p>
 */
@Service
public class ChatAdminQueryServiceImpl implements ChatAdminQueryService {

    /** 排队栏一次最多展示的条数（位置按该有序结果给出）。 */
    private static final int QUEUE_SCAN_LIMIT = 200;

    private final ChatSessionMapper chatSessionMapper;
    private final AgentPresenceRepository presenceRepository;
    private final ChatSatisfactionMapper satisfactionMapper;
    private final Clock clock;
    private final ChatAuditMapper auditMapper;
    private final ChatSessionNoteMapper noteMapper;
    private final ChatQueryService chatQueryService;
    private final ChatMetricsMapper metricsMapper;
    /** 客户展示信息（昵称 + 头像）：跨模块只经 customer 的 service 接口取，不碰对方表。 */
    private final CustomerQueryService customerQueryService;

    public ChatAdminQueryServiceImpl(ChatSessionMapper chatSessionMapper,
                                     AgentPresenceRepository presenceRepository,
                                     ChatSatisfactionMapper satisfactionMapper,
                                     Clock clock,
                                     ChatAuditMapper auditMapper,
                                     ChatSessionNoteMapper noteMapper,
                                     ChatQueryService chatQueryService,
                                     ChatMetricsMapper metricsMapper,
                                     CustomerQueryService customerQueryService) {
        this.chatSessionMapper = chatSessionMapper;
        this.presenceRepository = presenceRepository;
        this.satisfactionMapper = satisfactionMapper;
        this.clock = clock;
        this.auditMapper = auditMapper;
        this.noteMapper = noteMapper;
        this.chatQueryService = chatQueryService;
        this.customerQueryService = customerQueryService;
        this.metricsMapper = metricsMapper;
    }

    @Override
    public PageResult<?> sessions(PageQuery pageQuery) {
        Page<ChatSessionPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        // 不做全表排序：只按主键倒序，走主键索引；status 过滤由前端二次筛选（列表量级小）
        LambdaQueryWrapper<ChatSessionPO> wrapper = new LambdaQueryWrapper<ChatSessionPO>()
                .orderByDesc(ChatSessionPO::getId);
        IPage<ChatSessionPO> result = chatSessionMapper.selectPage(page, wrapper);
        List<SessionRow> rows = result.getRecords().stream()
                .map(ChatAdminQueryServiceImpl::toRow)
                .toList();
        return PageResult.of(rows, result.getTotal(), pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public List<?> workload() {
        return presenceRepository.findAll().stream()
                .sorted(Comparator.comparingLong(p -> p.getStaffId()))
                .map(p -> new WorkloadRow(p.getStaffId(), p.getPresence().name(),
                        p.getActiveSessions(), p.getMaxSessions()))
                .toList();
    }

    @Override
    public OpenSessionsResult openSessions(long staffId) {
        List<ChatSessionPO> activePos = chatSessionMapper.selectVisibleActiveByStaff(staffId);
        // 一次性批量取客户昵称/头像（2026-11-25：左侧列表要显示「进线用户是谁」）。
        // 查不到的客户留在 briefs 之外，行里给 null，前端兜底成「客户 {id}」。
        Map<Long, CustomerBrief> briefs = customerQueryService.briefsOf(
                activePos.stream().map(ChatSessionPO::getCustomerId).filter(Objects::nonNull).toList());
        List<OpenSessionsResult.Row> active = activePos.stream()
                .map(po -> {
                    CustomerBrief brief = briefs.get(po.getCustomerId());
                    return new OpenSessionsResult.Row(po.getId(), po.getStatus(), po.getStaffId(),
                            po.getCustomerId(), toMillis(po.getCreatedAt()), 0,
                            brief == null ? null : brief.nickname(),
                            brief == null ? null : brief.avatarFileId());
                })
                .toList();

        // 排队栏：先来后到（同一毫秒按主键），位置按序号给出，与客户端的排队位置口径一致
        // 2026-11-21 用户口径：离线坐席「看不到有多少人排队」，因此离线直接返回空队列；
        // 忙碌坐席仍可看队列（只是不自动接入），故只有 OFFLINE 隐藏。
        boolean offline = presenceRepository.findByStaffId(staffId)
                .map(p -> p.getPresence() == PresenceStatus.OFFLINE)
                .orElse(true);
        if (offline) {
            return new OpenSessionsResult(active, List.of());
        }
        List<ChatSessionPO> waitingPos = chatSessionMapper.selectVisibleWaitingFifo(QUEUE_SCAN_LIMIT);
        List<OpenSessionsResult.Row> waiting = IntStream.range(0, waitingPos.size())
                .mapToObj(i -> {
                    ChatSessionPO po = waitingPos.get(i);
                    return new OpenSessionsResult.Row(po.getId(), po.getStatus(), po.getStaffId(),
                            po.getCustomerId(), toMillis(po.getCreatedAt()), i + 1);
                })
                .toList();
        return new OpenSessionsResult(active, waiting);
    }

    @Override
    public TodayStatsResult todayStats(long staffId) {
        // 半开区间 [今天 00:00, 明天 00:00)：跨零点自动换日，无需定时任务清零
        LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());
        LocalDateTime from = today.atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();
        int closed = chatSessionMapper.countClosedByStaffBetween(staffId, from, to);
        int rated = satisfactionMapper.countRatedBetween(staffId, from, to);
        int satisfied = satisfactionMapper.countSatisfiedBetween(staffId, from, to);
        // 满意率 = 满意评价量 ÷ 有效评价总量 × 100%；无评价显示 -1（前端渲染 --）
        int ratePercent = rated == 0 ? -1 : (int) Math.round(satisfied * 100.0 / rated);
        return new TodayStatsResult(staffId, today.toString(), closed, rated, satisfied, ratePercent);
    }

    @Override
    public ChatAuditDtos.SessionDetail sessionDetail(long sessionId) {
        ChatSessionPO po = chatSessionMapper.selectById(sessionId);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 自动分配（SessionAssignmentUseCase）只改 status/staff_id，不一定写 started_at；
        // 审查 SQL 也用 COALESCE(started_at, created_at)，这里必须同口径，否则工单「咨询时间」会是 0。
        java.time.LocalDateTime started = po.getStartedAt() == null ? po.getCreatedAt() : po.getStartedAt();
        return new ChatAuditDtos.SessionDetail(po.getId(), po.getStatus(), po.getStaffId(), po.getCustomerId(),
                toMillis(started), toMillis(po.getEndedAt()), po.getCloseReason());
    }

    @Override
    public ChatAuditDtos.Transcript transcript(long sessionId) {
        ChatAuditDtos.SessionDetail detail = sessionDetail(sessionId);
        List<ChatAuditDtos.MessageLine> messages = chatQueryService.history(sessionId, 0).stream()
                .map(m -> new ChatAuditDtos.MessageLine(m.seq(), m.senderType(), m.msgType(),
                        m.content(), m.createdAtMillis()))
                .toList();
        return new ChatAuditDtos.Transcript(detail, messages, noteOf(detail));
    }

    /**
     * 工单读模型：联查客户资料的只读基础信息（昵称/联系方式）+ 会话开始时间作为咨询时间。
     * <p>客户资料属于 customer 模块，跨模块只走 {@link CustomerQueryService}，不碰对方 mapper。</p>
     */
    private ChatAuditDtos.NoteVO noteOf(ChatAuditDtos.SessionDetail detail) {
        ChatSessionNotePO po = noteMapper.selectBySessionId(detail.sessionId());
        CustomerContact contact = detail.customerId() == null ? null
                : customerQueryService.contactOf(detail.customerId());
        String nickname = contact == null ? null : contact.nickname();
        String phone = contact == null ? null : contact.phone();
        String email = contact == null ? null : contact.email();
        if (po == null) {
            // 工单还没填：仍然返回基础信息预填，客服第一次打开抽屉就不用抄客户资料。
            // summary/solution 等业务字段保持 null，前端据此判断「还没填」。
            return new ChatAuditDtos.NoteVO(detail.sessionId(), null, null, null, null, null,
                    detail.staffId(), 0L, null, null, null, false, null, null, null,
                    ChatSessionNote.SubmitStatus.DRAFT.name(), null,
                    nickname, phone, email, detail.startedAtMillis());
        }
        return new ChatAuditDtos.NoteVO(po.getSessionId(), po.getSummary(), po.getCategory(),
                po.getIssueInfo(), po.getResult(), po.getSolution(), po.getStaffId(),
                toMillis(po.getUpdatedAt()), po.getSubCategory(), po.getCustomerDemand(),
                po.getVerifiedInfo(), Boolean.TRUE.equals(po.getNeedFollowup()),
                po.getFollowupTime() == null ? null : toMillis(po.getFollowupTime()),
                po.getFollowupContact(), po.getAttachmentsJson(),
                po.getSubmitStatus() == null ? ChatSessionNote.SubmitStatus.DRAFT.name() : po.getSubmitStatus(),
                po.getSubmittedAt() == null ? null : toMillis(po.getSubmittedAt()),
                nickname, phone, email, detail.startedAtMillis());
    }

    @Override
    public List<ChatAuditDtos.HistoryRow> customerHistory(long customerId, long excludeSessionId, int limit) {
        // 用户要求「最多 10 次」，服务端硬上限，前端传多大都不放行
        int safeLimit = Math.max(1, Math.min(limit, 10));
        return auditMapper.selectHistory(customerId, excludeSessionId, safeLimit).stream()
                .map(r -> new ChatAuditDtos.HistoryRow(r.getSessionId(), r.getStaffId(), r.getStaffName(),
                        toMillis(r.getStartedAt()), toMillis(r.getEndedAt()), r.getCloseReason(),
                        r.getResult(), r.getMessageCount() == null ? 0 : r.getMessageCount()))
                .toList();
    }

    @Override
    public PageResult<ChatAuditDtos.AuditRow> audit(PageQuery pageQuery, Long staffId,
                                                     LocalDateTime from, LocalDateTime to,
                                                     String resultFilter, String keyword) {
        String result = blankToNull(resultFilter);
        String kw = blankToNull(keyword);
        Page<ChatAuditRowPO> page = new Page<>(pageQuery.getPage(), pageQuery.getSize());
        IPage<ChatAuditRowPO> dbPage = auditMapper.selectAuditPage(page, staffId, from, to, result, kw);
        long total = auditMapper.countAudit(staffId, from, to, result, kw);
        List<ChatAuditDtos.AuditRow> rows = dbPage.getRecords().stream()
                .map(r -> new ChatAuditDtos.AuditRow(r.getSessionId(), r.getCustomerId(), r.getStaffId(),
                        r.getStaffName(), toMillis(r.getStartedAt()), toMillis(r.getEndedAt()),
                        r.getSummary(), r.getResult(),
                        ChatSessionNote.SubmitStatus.SUBMITTED.name().equals(r.getSubmitStatus())
                                ? "已提交" : "草稿"))
                .toList();
        return PageResult.of(rows, total, pageQuery.getPage(), pageQuery.getSize());
    }

    @Override
    public List<ChatAuditDtos.AuditStaffRow> auditStaff() {
        return auditMapper.selectStaffOptions().stream()
                .map(s -> new ChatAuditDtos.AuditStaffRow(s.staffId(), s.name()))
                .toList();
    }

    @Override
    public ChatMetricsDtos.Result metrics(LocalDateTime from, LocalDateTime to, Long staffId) {
        // 半开区间 [from, to)。缺省 to 取「明天 00:00」而不是今天 23:59:59，
        // 这样最后一天的整秒数据不会被右边界切掉。
        LocalDateTime safeFrom = from == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : from;
        LocalDateTime safeTo = to == null
                ? LocalDate.ofInstant(clock.instant(), clock.getZone()).plusDays(1).atStartOfDay()
                : to;
        // 状态是「此刻」的值，不是区间聚合：现场 LEFT JOIN staff_presence 取回，
        // 指标页再用 /admin/chat/workload 定时刷新，保证筛选与展示始终同一份数据
        List<ChatMetricsDtos.Row> rows = metricsMapper.selectMetrics(safeFrom, safeTo, staffId).stream()
                .map(po -> new ChatMetricsDtos.Row(
                        po.getStaffId(), po.getStaffName(),
                        po.getClosedCount(), po.getRatedCount(), po.getSatisfiedCount(),
                        // 与工作台「今日满意率」同口径：无评价显示 --（-1），不是 0%
                        po.getRatedCount() == 0 ? -1
                                : (int) Math.round(po.getSatisfiedCount() * 100.0 / po.getRatedCount()),
                        po.getResolvedCount(),
                        po.getClosedCount() == 0 ? -1
                                : (int) Math.round(po.getResolvedCount() * 100.0 / po.getClosedCount()),
                        po.getAvgDurationSeconds(),
                        po.getPresence() == null ? "OFFLINE" : po.getPresence()))
                .toList();
        return new ChatMetricsDtos.Result(safeFrom.toLocalDate().toString(),
                safeTo.toLocalDate().minusDays(1).toString(), rows);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static SessionRow toRow(ChatSessionPO po) {        return new SessionRow(
                po.getId(),
                po.getStatus(),
                po.getStaffId(),
                po.getCustomerId(),
                toMillis(po.getCreatedAt()));
    }

    private static long toMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 会话列表行（字段与前端 ChatSessionVO 对齐）。 */
    public record SessionRow(long sessionId, String status, Long staffId, Long customerId,
                             long waitingSinceMillis) {
    }

    /** 客服工作量行：在线状态 + 当前负载 + 并发上限。 */
    public record WorkloadRow(long staffId, String presence, int activeSessions, int maxSessions) {
    }
}
