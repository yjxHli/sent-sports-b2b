package com.sent.sports.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sent.sports.chat.mapper.ChatSessionMapper;
import com.sent.sports.chat.mapper.po.ChatSessionPO;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.service.ChatAdminQueryService;
import com.sent.sports.chat.service.dto.OpenSessionsResult;
import com.sent.sports.chat.service.dto.TodayStatsResult;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
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
    private final Clock clock;

    public ChatAdminQueryServiceImpl(ChatSessionMapper chatSessionMapper,
                                     AgentPresenceRepository presenceRepository,
                                     Clock clock) {
        this.chatSessionMapper = chatSessionMapper;
        this.presenceRepository = presenceRepository;
        this.clock = clock;
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
        List<OpenSessionsResult.Row> active = chatSessionMapper.selectActiveByStaff(staffId).stream()
                .map(po -> new OpenSessionsResult.Row(po.getId(), po.getStatus(), po.getStaffId(),
                        po.getCustomerId(), toMillis(po.getCreatedAt()), 0))
                .toList();

        // 排队栏：先来后到（同一毫秒按主键），位置按序号给出，与客户端的排队位置口径一致
        List<ChatSessionPO> waitingPos = chatSessionMapper.selectWaitingFifo(QUEUE_SCAN_LIMIT);
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
        return new TodayStatsResult(staffId, today.toString(), closed);
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
