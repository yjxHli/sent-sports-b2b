package com.sent.sports.chat.controller.admin;

import com.sent.sports.chat.dto.admin.AdminChatDtos;
import com.sent.sports.chat.service.AssignSessionUseCase;
import com.sent.sports.chat.service.ChatAdminQueryService;
import com.sent.sports.chat.service.CloseSessionUseCase;
import com.sent.sports.chat.service.PresenceUseCase;
import com.sent.sports.chat.service.dto.AssignCmd;
import com.sent.sports.chat.service.dto.AssignSessionResult;
import com.sent.sports.chat.service.dto.CloseCmd;
import com.sent.sports.chat.service.dto.CloseSessionResult;
import com.sent.sports.chat.service.dto.SetPresenceCmd;
import com.sent.sports.chat.service.dto.PresenceResult;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    /** P5 绑定当前用户：阶段占位操作人 ID。 */
    private static final long OPERATOR_PLACEHOLDER = 0L;

    public AdminChatController(AssignSessionUseCase assignSessionUseCase,
                               CloseSessionUseCase closeSessionUseCase,
                               PresenceUseCase presenceUseCase,
                               ChatAdminQueryService chatAdminQueryService) {
        this.assignSessionUseCase = assignSessionUseCase;
        this.closeSessionUseCase = closeSessionUseCase;
        this.presenceUseCase = presenceUseCase;
        this.chatAdminQueryService = chatAdminQueryService;
    }

    @GetMapping("/sessions")
    public R<PageResult<?>> sessions(@ModelAttribute PageQuery page) {
        return R.ok(chatAdminQueryService.sessions(page));
    }

    @GetMapping("/workload")
    public R<java.util.List<?>> workload() {
        return R.ok(chatAdminQueryService.workload());
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
        return R.ok(new AdminChatDtos.CloseVO(r.sessionId(), r.status().name()));
    }
}
