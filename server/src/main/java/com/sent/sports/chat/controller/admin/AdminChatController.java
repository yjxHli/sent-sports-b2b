package com.sent.sports.chat.controller.admin;

import com.sent.sports.chat.dto.admin.AdminChatDtos;
import com.sent.sports.chat.service.SaveSessionNoteUseCase;
import com.sent.sports.chat.service.AssignSessionUseCase;
import com.sent.sports.chat.service.ChatAdminQueryService;
import com.sent.sports.chat.service.CloseSessionUseCase;
import com.sent.sports.chat.service.PresenceUseCase;
import com.sent.sports.chat.service.SendSatisfactionCardUseCase;
import com.sent.sports.chat.service.dto.AssignCmd;
import com.sent.sports.chat.service.dto.AssignSessionResult;
import com.sent.sports.chat.service.dto.CloseCmd;
import com.sent.sports.chat.service.dto.CloseSessionResult;
import com.sent.sports.chat.service.dto.OpenSessionsResult;
import com.sent.sports.chat.service.dto.SaveNoteCmd;
import com.sent.sports.chat.service.dto.SetPresenceCmd;
import com.sent.sports.chat.service.dto.PresenceResult;
import com.sent.sports.chat.service.dto.TodayStatsResult;
import com.sent.sports.chat.service.dto.TransferCmd;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.PageQuery;
import com.sent.sports.common.web.PageResult;
import com.sent.sports.common.web.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客服工作台控制器（P4-52）：三态列表 / 在线状态 / 接入 / 转接 / 关闭 / 工作量（plan §8.12）。
 * <p>依赖同模块用例（{@link AssignSessionUseCase} / {@link CloseSessionUseCase} / {@link PresenceUseCase}）
 * 与查询门面 {@link ChatAdminQueryService}（桩，P5 接入仓储）。操作人绑定（@CurrentUserArg）在 Phase 5，
 * 现以占位常量传入用例。接入按路径 {@code {id}} 精确接待该会话（{@code id<=0} 时退化为排队拉取）。</p>
 */
@RestController
@RequestMapping("/api/admin/chat")
public class AdminChatController {

    private final AssignSessionUseCase assignSessionUseCase;
    private final CloseSessionUseCase closeSessionUseCase;
    private final PresenceUseCase presenceUseCase;
    private final ChatAdminQueryService chatAdminQueryService;
    private final SendSatisfactionCardUseCase satisfactionCardUseCase;
    private final SaveSessionNoteUseCase saveSessionNoteUseCase;

    /** P5 绑定当前用户：阶段占位操作人 ID。 */
    private static final long OPERATOR_PLACEHOLDER = 0L;

    public AdminChatController(AssignSessionUseCase assignSessionUseCase,
                               CloseSessionUseCase closeSessionUseCase,
                               PresenceUseCase presenceUseCase,
                               ChatAdminQueryService chatAdminQueryService,
                               SendSatisfactionCardUseCase satisfactionCardUseCase,
                               SaveSessionNoteUseCase saveSessionNoteUseCase) {
        this.assignSessionUseCase = assignSessionUseCase;
        this.closeSessionUseCase = closeSessionUseCase;
        this.presenceUseCase = presenceUseCase;
        this.chatAdminQueryService = chatAdminQueryService;
        this.satisfactionCardUseCase = satisfactionCardUseCase;
        this.saveSessionNoteUseCase = saveSessionNoteUseCase;
    }

    @GetMapping("/sessions")
    public R<PageResult<?>> sessions(@ModelAttribute PageQuery page) {
        return R.ok(chatAdminQueryService.sessions(page));
    }

    /**
     * 工作台左侧两栏：该客服进行中 + 全员排队中（带排队位置）。
     * <p>不做分页：进行中上限就是并发上限（3），排队栏由服务端限长。
     */
    @GetMapping("/sessions/open")
    public R<OpenSessionsResult> openSessions(@RequestParam long staffId) {
        return R.ok(chatAdminQueryService.openSessions(staffId));
    }

    @GetMapping("/workload")
    public R<java.util.List<?>> workload() {
        return R.ok(chatAdminQueryService.workload());
    }

    /**
     * 今日完成接待数（工作台顶栏右侧常驻，跨零点自动归零）。
     * <p>口径：该客服今天结束（CLOSED）的会话数，按本地自然日 half-open 区间统计。
     */
    @GetMapping("/stats/today")
    public R<TodayStatsResult> todayStats(@RequestParam long staffId) {
        return R.ok(chatAdminQueryService.todayStats(staffId));
    }

    @PostMapping("/presence")
    @RequirePerm("chat:presence")
    public R<AdminChatDtos.PresenceVO> presence(@Valid @RequestBody AdminChatDtos.PresenceCmd cmd) {
        PresenceResult r = presenceUseCase.setPresence(
                new SetPresenceCmd(cmd.staffId(), cmd.toPresence(), OPERATOR_PLACEHOLDER));
        return R.ok(new AdminChatDtos.PresenceVO(r.staffId(), r.presence().name(), r.releasedSessions()));
    }

    @PostMapping("/sessions/{id}/accept")
    @RequirePerm("chat:assign")
    public R<AdminChatDtos.SessionActionVO> accept(@PathVariable long id,
                                                   @Valid @RequestBody AdminChatDtos.AcceptCmd cmd) {
        AssignSessionResult r = assignSessionUseCase.assign(
                new AssignCmd(id, cmd.staffId(), OPERATOR_PLACEHOLDER));
        return R.ok(new AdminChatDtos.SessionActionVO(r.sessionId(), r.status().name(), r.staffId()));
    }

    @PostMapping("/sessions/{id}/transfer")
    @RequirePerm("chat:transfer")
    public R<AdminChatDtos.SessionActionVO> transfer(@PathVariable long id,
                                                     @Valid @RequestBody AdminChatDtos.TransferCmd cmd) {
        AssignSessionResult r = assignSessionUseCase.transfer(
                new TransferCmd(id, cmd.toStaffId(), cmd.reason(), OPERATOR_PLACEHOLDER));
        return R.ok(new AdminChatDtos.SessionActionVO(r.sessionId(), r.status().name(), r.staffId()));
    }

    @PostMapping("/sessions/{id}/close")
    @RequirePerm("chat:close")
    public R<AdminChatDtos.CloseVO> close(@PathVariable long id,
                                          @Valid @RequestBody AdminChatDtos.CloseCmd cmd) {
        CloseSessionResult r = closeSessionUseCase.close(new CloseCmd(id, cmd.reason(), OPERATOR_PLACEHOLDER));
        return R.ok(new AdminChatDtos.CloseVO(r.sessionId(), r.status().name(), r.promotedSessionId()));
    }

    /** 客服主动发送「请评价本次服务」卡片（图片按钮上方入口）；同会话重复点幂等。 */
    @PostMapping("/sessions/{id}/satisfaction")
    @RequirePerm("chat:send")
    public R<java.util.Map<String, String>> sendSatisfactionCard(@PathVariable long id) {
        String messageId = satisfactionCardUseCase.send(id);
        return R.ok(java.util.Map.of("messageId", messageId));
    }

    /** 单会话状态：工作台轮询据此把已结束 Tab 置灰，而不是直接移除。 */
    @GetMapping("/sessions/{id}")
    @RequirePerm("chat:serve")
    public R<?> sessionDetail(@PathVariable long id) {
        return R.ok(chatAdminQueryService.sessionDetail(id));
    }

    /** 会话回放：历史服务弹窗与客服审查详情共用。 */
    @GetMapping("/sessions/{id}/transcript")
    @RequirePerm("chat:serve")
    public R<?> transcript(@PathVariable long id) {
        return R.ok(chatAdminQueryService.transcript(id));
    }

    /** 当前客服查看同一客户最近的服务记录（服务端硬上限 10）。 */
    @GetMapping("/customers/{customerId}/sessions")
    @RequirePerm("chat:serve")
    public R<?> customerHistory(@PathVariable long customerId,
                                @RequestParam(defaultValue = "10") int limit,
                                @RequestParam(defaultValue = "0") long excludeSessionId) {
        return R.ok(chatAdminQueryService.customerHistory(customerId, excludeSessionId, limit));
    }

    /**
     * 服务工单详情：除工单字段外，服务端自动带入客户昵称、联系方式与咨询时间（会话开始时间）。
     * 这些基础信息只读，客服无需手填；主管审查页复用同一接口并排展示。
     */
    @GetMapping("/sessions/{id}/note")
    @RequirePerm("chat:serve")
    public R<?> note(@PathVariable long id) {
        return R.ok(chatAdminQueryService.transcript(id).note());
    }

    @PutMapping("/sessions/{id}/note")
    @RequirePerm("chat:note")
    public R<?> saveNote(@PathVariable long id, @Valid @RequestBody AdminChatDtos.SaveNoteCmd cmd) {
        return R.ok(saveSessionNoteUseCase.save(new SaveNoteCmd(id, cmd.staffId(), cmd.summary(),
                cmd.category(), cmd.info(), cmd.result(), cmd.solution(),
                cmd.subCategory(), cmd.customerDemand(), cmd.verifiedInfo(),
                Boolean.TRUE.equals(cmd.needFollowup()), cmd.followupTime(), cmd.followupContact(),
                cmd.attachmentsJson(), !Boolean.FALSE.equals(cmd.submit()))));
    }

    /** 客服审查：仅超管与客服经理（exact=true，不吃 '*' 通配）。 */
    @GetMapping("/audit/sessions")
    @RequirePerm(value = "chat:audit", exact = true)
    public R<?> auditSessions(@ModelAttribute PageQuery page,
                              @RequestParam(required = false) Long staffId,
                              @RequestParam(required = false) String from,
                              @RequestParam(required = false) String to,
                              @RequestParam(required = false) String result,
                              @RequestParam(required = false) String keyword) {
        return R.ok(chatAdminQueryService.audit(page, staffId, parseDate(from, false),
                parseDate(to, true), result, keyword));
    }

    @GetMapping("/audit/staff")
    @RequirePerm(value = "chat:audit", exact = true)
    public R<?> auditStaff() {
        return R.ok(chatAdminQueryService.auditStaff());
    }

    /**
     * 客服指标（2026-11-18 用户要求）：今天 / 近 7 天 / 近 30 天 / 自定义时间。
     * 复用审查权限：只让超管与客服经理看全员绩效，普通客服看不到别人的数据。
     */
    @GetMapping("/metrics")
    @RequirePerm(value = "chat:audit", exact = true)
    public R<?> metrics(@RequestParam(required = false) Long staffId,
                        @RequestParam(required = false) String from,
                        @RequestParam(required = false) String to) {
        // 半开区间 [from, to+1天)：端点取次日 00:00，才能含住 to 当天的全部数据；
        // 服务层回显的 to = safeTo 减一天，正好是用户选的那一天（不能传当天 23:59:59 再减）。
        java.time.LocalDateTime toExclusive = parseDate(to, false);
        return R.ok(chatAdminQueryService.metrics(parseDate(from, false),
                toExclusive == null ? null : toExclusive.plusDays(1), staffId));
    }

    /** 支持 yyyy-MM-dd 与完整 ISO 时间；to 取当天 23:59:59.999 由服务层半开区间处理。 */
    private static java.time.LocalDateTime parseDate(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        if (text.length() == 10) {
            return java.time.LocalDate.parse(text).atTime(endOfDay
                    ? java.time.LocalTime.MAX : java.time.LocalTime.MIN);
        }
        return java.time.LocalDateTime.parse(text);
    }
}
