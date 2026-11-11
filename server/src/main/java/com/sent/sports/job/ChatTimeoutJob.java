package com.sent.sports.job;

import com.sent.sports.chat.mapper.ChatSessionMapper;
import com.sent.sports.chat.mapper.po.ChatSessionPO;
import com.sent.sports.common.error.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话超时结束（P5-45）：扫描 30 分钟无消息的 ACTIVE 会话，以 TIMEOUT 原因条件关闭。
 * <ul>
 *   <li><b>幂等</b>：{@code closeSession} 是「WHERE status = 'ACTIVE'」的条件更新，返回 0 行即表示
 *       并发下已被他人关闭，按冲突跳过；重复执行不会二次关闭，无需分布式锁；</li>
 *   <li><b>批量上限</b>：每轮最多 {@value #BATCH_SIZE} 个会话；</li>
 *   <li><b>不中断整轮</b>：单个抛 {@link BizException} 只跳过该会话，继续处理其余会话。</li>
 * </ul>
 */
@Component
public class ChatTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(ChatTimeoutJob.class);
    private static final int BATCH_SIZE = 200;
    private static final int IDLE_MINUTES = 30;
    private static final String CLOSE_REASON = "TIMEOUT";

    private final ChatSessionMapper chatSessionMapper;
    private final Clock clock;

    public ChatTimeoutJob(ChatSessionMapper chatSessionMapper, Clock clock) {
        this.chatSessionMapper = chatSessionMapper;
        this.clock = clock;
    }

    /** 每分钟扫一轮。 */
    @Scheduled(fixedDelayString = "${app.job.chat-timeout-ms:60000}")
    public void scheduled() {
        int closed = runOnce();
        if (closed > 0) {
            log.info("会话超时结束：本轮关闭 {} 个会话", closed);
        }
    }

    /** 执行一轮超时关闭，返回成功关闭的会话数（便于单测断言）。 */
    public int runOnce() {
        LocalDateTime before = LocalDateTime.now(clock).minusMinutes(IDLE_MINUTES);
        List<ChatSessionPO> sessions = chatSessionMapper.selectIdleSessions(before, BATCH_SIZE);
        int closed = 0;
        for (ChatSessionPO session : sessions) {
            try {
                int rows = chatSessionMapper.closeSession(session.getId(), CLOSE_REASON);
                if (rows > 0) {
                    closed++;
                } else {
                    log.debug("跳过会话 {}：条件更新未命中（已被并发关闭）", session.getId());
                }
            } catch (BizException e) {
                log.debug("跳过会话 {}：{}", session.getId(), e.getMessage());
            }
        }
        return closed;
    }
}
