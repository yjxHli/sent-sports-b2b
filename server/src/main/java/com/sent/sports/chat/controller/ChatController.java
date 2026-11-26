package com.sent.sports.chat.controller;

import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.domain.SessionType;
import com.sent.sports.chat.dto.ChatDtos;
import com.sent.sports.chat.service.ChatQueryService;
import com.sent.sports.chat.service.CreateSessionUseCase;
import com.sent.sports.chat.dto.SatisfactionDtos;
import com.sent.sports.chat.service.RateSatisfactionUseCase;
import com.sent.sports.chat.service.SendMessageUseCase;
import com.sent.sports.chat.service.dto.CreateSessionCmd;
import com.sent.sports.chat.service.dto.CreateSessionResult;
import com.sent.sports.chat.service.dto.SendMessageCmd;
import com.sent.sports.chat.service.dto.SendMessageResult;
import com.sent.sports.common.anno.RequirePerm;
import com.sent.sports.common.web.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客服会话控制器（P4-31）：发起会话 / 发消息 / afterSeq 补拉 / 已读上报（plan §8.9）。
 * <p>写面依赖同模块用例 {@link CreateSessionUseCase} / {@link SendMessageUseCase}；
 * 读面依赖查询门面 {@link ChatQueryService}（桩，P5 接入仓储）。
 * 当前用户绑定（@CurrentUserArg）在 Phase 5，发消息/建会话的操作人（operatorId）以占位常量，真实绑定 P5。</p>
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    /** 占位常量：Phase 5 由 @CurrentUserArg 注入真实当前用户 / 操作人。 */
    private static final long CURRENT_USER_PLACEHOLDER = 0L;

    private final CreateSessionUseCase createSessionUseCase;
    private final SendMessageUseCase sendMessageUseCase;
    private final ChatQueryService chatQueryService;
    private final RateSatisfactionUseCase rateSatisfactionUseCase;

    public ChatController(CreateSessionUseCase createSessionUseCase, SendMessageUseCase sendMessageUseCase,
                          ChatQueryService chatQueryService,
                          RateSatisfactionUseCase rateSatisfactionUseCase) {
        this.createSessionUseCase = createSessionUseCase;
        this.sendMessageUseCase = sendMessageUseCase;
        this.chatQueryService = chatQueryService;
        this.rateSatisfactionUseCase = rateSatisfactionUseCase;
    }

    /**
     * 进入聊天页时只查当前未结束会话，不创建会话。
     * 客户必须发出首条消息才算咨询；工作台只展示已有客户消息的会话。
     */
    @GetMapping("/sessions/current")
    public R<ChatDtos.SessionVO> currentOpen(@RequestParam long customerId) {
        return R.ok(chatQueryService.currentOpen(customerId));
    }

    /** 历史咨询 / 服务进度列表：只返回会话摘要，正文按需再用 /sessions/{id}/messages 拉。 */
    @GetMapping("/sessions")
    public R<List<ChatDtos.SessionSummaryVO>> sessions(@RequestParam long customerId,
                                                       @RequestParam(defaultValue = "20") int limit) {
        return R.ok(chatQueryService.listSessions(customerId, limit));
    }

    @PostMapping("/sessions")
    @RequirePerm("chat:create")
    public R<ChatDtos.SessionVO> createSession(@Valid @RequestBody ChatDtos.CreateSessionCmdReq cmd) {
        CreateSessionResult r = createSessionUseCase.create(
                new CreateSessionCmd(cmd.customerId(), cmd.vip(), CURRENT_USER_PLACEHOLDER,
                        SessionType.of(cmd.sessionType())));
        return R.ok(new ChatDtos.SessionVO(r.sessionId(), r.status().name(), r.staffId(), r.reused(),
                r.sessionType().name()));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    @RequirePerm("chat:send")
    public R<ChatDtos.MessageVO> sendMessage(@PathVariable long sessionId,
                                            @Valid @RequestBody ChatDtos.SendCmd cmd) {
        SendMessageResult r = sendMessageUseCase.send(new SendMessageCmd(
                sessionId, cmd.senderType(), cmd.content(), cmd.clientMsgId(), CURRENT_USER_PLACEHOLDER,
                cmd.msgType()));
        return R.ok(new ChatDtos.MessageVO(r.messageId(), r.sessionId(), cmd.senderType().name(),
                cmd.msgType() == null ? "TEXT" : cmd.msgType(), cmd.content(), cmd.clientMsgId(), r.seq(), 0L));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public R<List<ChatDtos.MessageVO>> history(@PathVariable long sessionId,
                                               @RequestParam(defaultValue = "0") int afterSeq) {
        return R.ok(chatQueryService.history(sessionId, afterSeq));
    }

    /** 排队状态（用户端轮询）：WAITING 时返回前方人数 +1 的位置，已接入返回 position=0。 */
    @GetMapping("/sessions/{sessionId}/queue")
    public R<ChatDtos.QueueVO> queue(@PathVariable long sessionId) {
        return R.ok(chatQueryService.queue(sessionId));
    }

    @PostMapping("/sessions/{sessionId}/read")
    public R<Void> reportRead(@PathVariable long sessionId,
                              @Valid @RequestBody ChatDtos.ReportReadCmd cmd) {
        chatQueryService.reportRead(sessionId, cmd.seq());
        return R.ok();
    }

    /**
     * 客户给本次服务打分（满意 / 一般 / 不满意）。会话已结束也允许——评价卡正是结束时发出来的。
     * 重复提交由 uk_satisfaction_session 幂等返回原档位。
     */
    @PostMapping("/sessions/{sessionId}/satisfaction")
    @RequirePerm("chat:send")
    public R<SatisfactionDtos.RateResult> rate(@PathVariable long sessionId,
                                               @Valid @RequestBody SatisfactionDtos.RateCmd cmd) {
        return R.ok(rateSatisfactionUseCase.rate(sessionId, cmd.score()));
    }
}
