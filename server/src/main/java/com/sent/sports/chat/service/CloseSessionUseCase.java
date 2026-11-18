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
 * 超时自动关闭(closeExpired)：关闭 inactive 超过 {@link #TIMEOUT_MILLIS} 的 ACTIVE/WAITING 会话(原因 TIMEOUT)。
 * <p><b>自动补位（并发调度核心）</b>：结束一个 ACTIVE 会话时先把接待客服负载 -1，
 * 紧接着从队列取「最早 且 分配对象是该客服或未指定」的 WAITING 会话转为 ACTIVE、负载 +1，
 * 并通过 {@link ChatNotifier} 推送「已接入客服」。因此满负荷 3 人时「关一个、接一个」，
 * 负载恒定 3，客户侧排队提示同步消失。
 * <p>仅依赖 chat 模块内部仓储端口与 common，符合 ArchUnit 规则 4。
 */
@Service
public class CloseSessionUseCase {

    /**
     * 空闲阈值（2026-11-16 用户要求）：客户 10 分钟没发消息即视为会话结束。
     * <p>与定时任务 {@code ChatTimeoutJob#DEFAULT_IDLE_MINUTES} 保持同一口径，
     * 两条自动结束路径（定时扫 ACTIVE / 用例扫全部未结束）不能各用一套阈值。</p>
     */
    public static final long TIMEOUT_MILLIS = 10L * 60 * 1000; // 10 分钟

    private final ChatSessionRepository sessionRepository;
    private final AgentPresenceRepository presenceRepository;
    private final ChatNotifier notifier;
    /** 会话结束自动补发评价卡（2026-11-17 用户要求）。 */
    private final SendSatisfactionCardUseCase satisfactionCardUseCase;

    public CloseSessionUseCase(ChatSessionRepository sessionRepository,
                               AgentPresenceRepository presenceRepository,
                               ChatNotifier notifier,
                               SendSatisfactionCardUseCase satisfactionCardUseCase) {
        this.sessionRepository = sessionRepository;
        this.presenceRepository = presenceRepository;
        this.notifier = notifier;
        this.satisfactionCardUseCase = satisfactionCardUseCase;
    }

    /** 推送会话结束帧；通知失败不能阻断关单/队列补位。 */
    private void notifyClosed(ChatSession session) {
        try {
            notifier.sessionClosed(session.getCustomerId(), session.getId(),
                    session.getStaffId(), session.getCloseReason());
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(CloseSessionUseCase.class)
                    .warn("会话 {} 结束通知推送失败，不影响结束", session.getId(), e);
        }
    }

    /** 结束前补发评价卡；卡片失败不能阻断关单/队列补位这条主链路。 */
    private void autoSendSatisfactionCard(long sessionId) {
        try {
            satisfactionCardUseCase.send(sessionId);
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(CloseSessionUseCase.class)
                    .warn("会话 {} 评价卡补发失败，不影响结束会话", sessionId, e);
        }
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
        autoSendSatisfactionCard(cmd.sessionId());
        session.close(cmd.reason());
        sessionRepository.save(session);
        notifyClosed(session);

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

    /**
     * 超时关闭单个会话（定时任务 {@code ChatTimeoutJob} 的落库出口）。
     *
     * <p>为什么不让任务直接 {@code UPDATE ... SET status='CLOSED'}：那样坐席负载 {@code active_sessions}
     * 不会 -1，10 分钟自动结束跑几轮就把接待名额占死（新客户只能排队，工作台表现为「客服像掉线」）。
     * 名额释放 + 队列补位与人工结束走完全相同的口径，避免两条路径各写一套。</p>
     *
     * @return 是否真的关闭了（已结束 / 不存在返回 false，任务按跳过处理）
     */
    @UseCase
    public boolean timeoutClose(long sessionId) {
        Optional<ChatSession> found = sessionRepository.findById(sessionId);
        if (found.isEmpty()) {
            return false;
        }
        ChatSession session = found.get();
        if (session.getStatus() == ChatSessionStatus.CLOSED) {
            return false;
        }
        Long staffId = session.getStaffId();
        boolean releasing = session.getStatus() == ChatSessionStatus.ACTIVE;
        autoSendSatisfactionCard(sessionId);
        session.close(ChatCloseReason.TIMEOUT.name());
        sessionRepository.save(session);
        notifyClosed(session);
        if (releasing && staffId != null) {
            presenceRepository.findByStaffId(staffId).ifPresent(presence -> {
                presence.decreaseActive();
                presenceRepository.save(presence);
            });
            promoteNextWaiting(staffId);
        }
        return true;
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
                autoSendSatisfactionCard(session.getId());
                session.close(ChatCloseReason.TIMEOUT.name());
                sessionRepository.save(session);
                notifyClosed(session);
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
