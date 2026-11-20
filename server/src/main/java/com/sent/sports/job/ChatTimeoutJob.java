package com.sent.sports.job;

import com.sent.sports.chat.service.ChatJobQueryService;
import com.sent.sports.chat.service.CloseSessionUseCase;
import com.sent.sports.common.error.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话超时结束（P5-45 / 2026-11-16 用户要求收紧到 10 分钟）：扫描空闲超过 {@code app.chat.idle-minutes}
 * （默认 {@value #DEFAULT_IDLE_MINUTES}）分钟的 ACTIVE 会话，以 TIMEOUT 原因结束。
 * <p><b>空闲口径</b>：{@code last_msg_at} = 双方最后一条消息时间（客户/客服发消息都会刷新），
 * 所以客服正在对话不会被误关；会话创建时也写一次，避免「刚进线还没说话」被当成永久空闲。
 * <ul>
 *   <li><b>落库走用例</b>：实际关闭交给 {@link CloseSessionUseCase#timeoutClose(long)}，
 *       坐席负载 -1 与队列补位跟人工结束同一口径。任务本身只做「扫描 + 批量上限」，
 *       不让 SQL 直接改状态，否则 10 分钟规则跑几轮就把接待名额占死；</li>
 *   <li><b>幂等</b>：用例对已结束会话返回 false、抛 {@link BizException} 也只跳过，
 *       重复执行不会二次关闭，无需分布式锁；</li>
 *   <li><b>批量上限</b>：每轮最多 {@value #BATCH_SIZE} 个会话；</li>
 *   <li><b>不中断整轮</b>：单个会话异常只跳过它，继续处理其余会话；</li>
 *   <li><b>只管 ACTIVE</b>：WAITING 会话不占坐席名额、且客户下次进线会复用同一条，
 *       提前结束它只会让「正在排队」的客户页面失效，故不纳入本任务。</li>
 * </ul>
 */
@Component
public class ChatTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(ChatTimeoutJob.class);
    private static final int BATCH_SIZE = 200;

    /** 客户无消息自动结束会话的默认阈值（分钟），可用 {@code app.chat.idle-minutes} 覆盖。 */
    public static final int DEFAULT_IDLE_MINUTES = 10;

    private final ChatJobQueryService chatJobQueryService;
    private final CloseSessionUseCase closeSessionUseCase;
    private final Clock clock;
    private final int idleMinutes;

    public ChatTimeoutJob(ChatJobQueryService chatJobQueryService, CloseSessionUseCase closeSessionUseCase,
                          Clock clock, @Value("${app.chat.idle-minutes:10}") int idleMinutes) {
        this.chatJobQueryService = chatJobQueryService;
        this.closeSessionUseCase = closeSessionUseCase;
        this.clock = clock;
        this.idleMinutes = idleMinutes <= 0 ? DEFAULT_IDLE_MINUTES : idleMinutes;
    }

    /** 每分钟扫一轮。 */
    @Scheduled(fixedDelayString = "${app.job.chat-timeout-ms:60000}")
    public void scheduled() {
        int closed = runOnce();
        if (closed > 0) {
            log.info("会话超时结束：本轮关闭 {} 个会话（空闲 {} 分钟）", closed, idleMinutes);
        }
    }

    /** 执行一轮超时关闭，返回成功关闭的会话数（便于单测断言）。 */
    public int runOnce() {
        LocalDateTime before = LocalDateTime.now(clock).minusMinutes(idleMinutes);
        List<Long> sessionIds = chatJobQueryService.findIdleSessionIds(before, BATCH_SIZE);
        int closed = 0;
        for (Long sessionId : sessionIds) {
            try {
                if (closeSessionUseCase.timeoutClose(sessionId)) {
                    closed++;
                } else {
                    log.debug("跳过会话 {}：已结束或不存在", sessionId);
                }
            } catch (BizException e) {
                log.debug("跳过会话 {}：{}", sessionId, e.getMessage());
            }
        }
        return closed;
    }
}
