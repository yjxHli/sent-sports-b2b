package com.sent.sports.chat.service;

import com.sent.sports.chat.domain.ChatCloseReason;
import com.sent.sports.chat.domain.ChatSession;
import com.sent.sports.chat.domain.ChatSessionStatus;
import com.sent.sports.chat.repository.AgentPresenceRepository;
import com.sent.sports.chat.repository.ChatSessionRepository;
import com.sent.sports.chat.service.dto.CloseCmd;
import com.sent.sports.chat.service.dto.CloseExpiredResult;
import com.sent.sports.chat.service.dto.CloseSessionResult;
import com.sent.sports.common.anno.UseCase;
import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 结束会话用例（P3-56/57）：spec §6.10——
 * 主动关闭(原因取自固定枚举 DONE/TIMEOUT/LEFT/TRANSFERRED)；
 * 超时自动关闭(closeExpired)：关闭 inactive 超过 30 分钟的 ACTIVE/WAITING 会话(原因 TIMEOUT)。
 * <p><b>自动补位（并发调度核心）</b>：结束一个 ACTIVE 会话时先把接待客服负载 -1，
 * 紧接着从队列取「最早 且 分配对象是该客服或未指定」的 WAITING 会话转为 ACTIVE、负载 +1，
 * 并通过 {@link ChatNotifier} 推送「已接入客服」。因此满负荷 3 人时「关一个、接一个」，
 * 负载恒定 3，客户侧排队提示同步消失。
 * <p>仅依赖 chat 模块内部仓储端口与 common，符合 ArchUnit 规则 4。
 */
@Service
public class CloseSessionUseCase {

    public static final long TIMEOUT_MILLIS = 30L * 60 * 1000; // 30 分钟

    private final ChatSessionRepository sessionRepository;
    private final AgentPresenceRepository presenceRepository;
    private final ChatNotifier notifier;

    public CloseSessionUseCase(ChatSessionRepository sessionRepository,
                               AgentPresenceRepository presenceRepository,
                               ChatNotifier notifier) {
        this.sessionRepository = sessionRepository;
        this.presenceRepository = presenceRepository;
        this.notifier = notifier;
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
        Long staffId = session.getStaffId();
        boolean releasing = session.getStatus() == ChatSessionStatus.ACTIVE;
        session.close(cmd.reason());
        sessionRepository.save(session);

        Long promotedSessionId = null;
        if (releasing && staffId != null) {
            presenceRepository.findByStaffId(staffId).ifPresent(presence -> {
                presence.decreaseActive();
                presenceRepository.save(presence);
            });
            promotedSessionId = promoteNextWaiting(staffId);
        }
        return new CloseSessionResult(session.getId(), session.getStatus(), promotedSessionId);
    }

    /**
     * 自动补位：取该客服可接的最早排队会话并接入，负载 +1，推送接入通知。
     *
     * @return 被补位的会话号；队列为空返回 null
     */
    private Long promoteNextWaiting(long staffId) {
        Optional<ChatSession> picked = sessionRepository.findWaiting().stream()
                // 分配给别人的排队会话不抢；未指定分配对象（常态）或本就指向该客服的才可接
                .filter(s -> s.getStaffId() == null || s.getStaffId() == staffId)
                .min(Comparator.comparingLong(ChatSession::getCreatedAtMillis)
                        .thenComparingLong(ChatSession::getId));
        if (picked.isEmpty()) {
            return null;
        }
        ChatSession next = picked.get();
        next.assign(staffId);
        sessionRepository.save(next);
        presenceRepository.findByStaffId(staffId).ifPresent(presence -> {
            presence.increaseActive();
            presenceRepository.save(presence);
        });
        notifier.sessionAssigned(next.getCustomerId(), next.getId(), staffId);
        return next.getId();
    }

    @UseCase
    public CloseExpiredResult closeExpired(long nowMillis) {
        List<ChatSession> open = new ArrayList<>();
        open.addAll(sessionRepository.findByStatus(ChatSessionStatus.ACTIVE));
        open.addAll(sessionRepository.findByStatus(ChatSessionStatus.WAITING));

        List<Long> closed = new ArrayList<>();
        for (ChatSession session : open) {
            if (session.isExpired(nowMillis, TIMEOUT_MILLIS)) {
                Long staffId = session.getStaffId();
                boolean releasing = session.getStatus() == ChatSessionStatus.ACTIVE;
                session.close(ChatCloseReason.TIMEOUT.name());
                sessionRepository.save(session);
                if (releasing && staffId != null) {
                    // 超时关闭同样释放负载并补位，否则长时间运行后名额会被「幽灵会话」占死
                    presenceRepository.findByStaffId(staffId).ifPresent(presence -> {
                        presence.decreaseActive();
                        presenceRepository.save(presence);
                    });
                    promoteNextWaiting(staffId);
                }
                closed.add(session.getId());
            }
        }
        return new CloseExpiredResult(closed.size(), closed);
    }
}
