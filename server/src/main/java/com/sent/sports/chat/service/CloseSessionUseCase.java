package com.sent.sports.chat.service;

import com.sent.sports.chat.domain.ChatCloseReason;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.dto.CloseCmd;
import com.sent.sports.chat.service.dto.CloseExpiredResult;
import com.sent.sports.chat.service.dto.CloseSessionResult;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 结束会话用例（P3-56/57）：spec §6.10——
 * 主动关闭(原因取自固定枚举 DONE/TIMEOUT/LEFT/TRANSFERRED)；
 * 超时自动关闭(closeExpired)：关闭 inactive 超过 30 分钟的 ACTIVE/WAITING 会话(原因 TIMEOUT)。
 * 仅依赖 chat 模块内部仓储端口与 common，符合 ArchUnit 规则 4。
 */
@Service
public class CloseSessionUseCase {

    public static final long TIMEOUT_MILLIS = 30L * 60 * 1000; // 30 分钟

    private final ChatSessionRepository sessionRepository;

    public CloseSessionUseCase(ChatSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @UseCase
    public CloseSessionResult close(CloseCmd cmd) {
        try {
            ChatCloseReason.valueOf(cmd.reason());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        ChatSession session = sessionRepository.findById(cmd.sessionId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND));
        session.close(cmd.reason());
        sessionRepository.save(session);
        return new CloseSessionResult(session.getId(), session.getStatus());
    }

    @UseCase
    public CloseExpiredResult closeExpired(long nowMillis) {
        List<ChatSession> open = new ArrayList<>();
        open.addAll(sessionRepository.findByStatus(ChatSessionStatus.ACTIVE));
        open.addAll(sessionRepository.findByStatus(ChatSessionStatus.WAITING));

        List<Long> closed = new ArrayList<>();
        for (ChatSession session : open) {
            if (session.isExpired(nowMillis, TIMEOUT_MILLIS)) {
                session.close(ChatCloseReason.TIMEOUT.name());
                sessionRepository.save(session);
                closed.add(session.getId());
            }
        }
        return new CloseExpiredResult(closed.size(), closed);
    }
}
