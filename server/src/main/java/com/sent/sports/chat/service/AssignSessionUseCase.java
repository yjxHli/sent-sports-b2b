package com.sent.sports.chat.service;

import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.dto.AssignCmd;
import com.sent.sports.chat.service.dto.AssignSessionResult;
import com.sent.sports.chat.service.dto.TransferCmd;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 分配与转接用例（P3-54/55）：spec §6.10——
 * 分配：指定 {@code sessionId} 时接待该会话；未指定则从 WAITING 队列按 VIP 优先、再按最早创建拉取一个；
 * 转接：ACTIVE 会话换接待客服，原因必填，历史消息保留。
 * 仅依赖 chat 模块内部仓储端口与 common，符合 ArchUnit 规则 4。
 */
@Service
public class AssignSessionUseCase {

    private final ChatSessionRepository sessionRepository;

    public AssignSessionUseCase(ChatSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @UseCase
    public AssignSessionResult assign(AssignCmd cmd) {
        // 指定会话：工作台点「接待」必须接待用户看到的那一条，
        // 否则会退化成「拉取队列里最早的一条」，把别的客户的会话接待掉。
        if (cmd.sessionId() > 0) {
            ChatSession target = sessionRepository.findById(cmd.sessionId())
                    .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
            target.assign(cmd.staffId()); // 非 WAITING → STATE_CONFLICT(40901)
            sessionRepository.save(target);
            return new AssignSessionResult(target.getId(), target.getStatus(), target.getStaffId());
        }
        List<ChatSession> waiting = sessionRepository.findWaiting();
        ChatSession next = waiting.stream()
                .sorted(Comparator.comparing(ChatSession::isVip).reversed()
                        .thenComparingLong(ChatSession::getCreatedAtMillis))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.BIZ_REJECT));
        next.assign(cmd.staffId());
        sessionRepository.save(next);
        return new AssignSessionResult(next.getId(), next.getStatus(), next.getStaffId());
    }

    @UseCase
    public AssignSessionResult transfer(TransferCmd cmd) {
        ChatSession session = sessionRepository.findById(cmd.sessionId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        if (cmd.reason() == null || cmd.reason().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        session.reassign(cmd.toStaffId());
        sessionRepository.save(session);
        return new AssignSessionResult(session.getId(), session.getStatus(), session.getStaffId());
    }
}
