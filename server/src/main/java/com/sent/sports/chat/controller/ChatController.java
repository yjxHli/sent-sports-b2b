package com.sent.sports.chat.controller;

import com.sent.sports.chat.domain.SenderType;
import com.sent.sports.chat.dto.ChatDtos;
import com.sent.sports.chat.service.ChatQueryService;
import com.sent.sports.chat.service.CreateSessionUseCase;
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

    public ChatController(CreateSessionUseCase createSessionUseCase, SendMessageUseCase sendMessageUseCase,
                          ChatQueryService chatQueryService) {
        this.createSessionUseCase = createSessionUseCase;
        this.sendMessageUseCase = sendMessageUseCase;
        this.chatQueryService = chatQueryService;
    }

    @PostMapping("/sessions")
    @RequirePerm("chat:create")
    public R<ChatDtos.SessionVO> createSession(@Valid @RequestBody ChatDtos.CreateSessionCmdReq cmd) {
        CreateSessionResult r = createSessionUseCase.create(
                new CreateSessionCmd(cmd.customerId(), cmd.vip(), CURRENT_USER_PLACEHOLDER));
        return R.ok(new ChatDtos.SessionVO(r.sessionId(), r.status().name(), r.staffId(), r.reused()));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    @RequirePerm("chat:send")
    public R<ChatDtos.MessageVO> sendMessage(@PathVariable long sessionId,
                                            @Valid @RequestBody ChatDtos.SendCmd cmd) {
        SendMessageResult r = sendMessageUseCase.send(new SendMessageCmd(
                sessionId, cmd.senderType(), cmd.content(), cmd.clientMsgId(), CURRENT_USER_PLACEHOLDER));
        return R.ok(new ChatDtos.MessageVO(r.messageId(), r.sessionId(), null, null, null, r.seq(), 0L));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public R<List<ChatDtos.MessageVO>> history(@PathVariable long sessionId,
                                               @RequestParam(defaultValue = "0") int afterSeq) {
        return R.ok(chatQueryService.history(sessionId, afterSeq));
    }

    @PostMapping("/sessions/{sessionId}/read")
    public R<Void> reportRead(@PathVariable long sessionId,
                              @Valid @RequestBody ChatDtos.ReportReadCmd cmd) {
        chatQueryService.reportRead(sessionId, cmd.seq());
        return R.ok();
    }
}
