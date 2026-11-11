package com.sent.sports.job;

import com.sent.sports.chat.ws.SessionBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * WebSocket 心跳回收（P5-34 心跳验收）：定时关闭长时间无上行流量的僵死连接。
 * <p>浏览器 WebSocket 的控制帧由浏览器自身处理，前端不一定发应用层 PING；
 * 因此后端以「最后一次上行报文时间」为准，超时即由广播器关闭并清理注册表。
 * 单实例进程内注册表即权威源，不需要 Redis，也不需要分布式锁。
 */
@Component
public class WsHeartbeatJob {

    private static final Logger log = LoggerFactory.getLogger(WsHeartbeatJob.class);

    private final SessionBroadcaster broadcaster;
    private final long idleTimeoutMillis;

    public WsHeartbeatJob(SessionBroadcaster broadcaster,
                          @Value("${app.job.ws-idle-timeout-ms:300000}") long idleTimeoutMillis) {
        this.broadcaster = broadcaster;
        this.idleTimeoutMillis = idleTimeoutMillis;
    }

    @Scheduled(fixedDelayString = "${app.job.ws-heartbeat-ms:60000}")
    public void scheduled() {
        int closed = runOnce();
        if (closed > 0) {
            log.info("WebSocket 心跳回收：本轮清理 {} 个空闲连接", closed);
        }
    }

    /** 执行一轮回收，返回关闭的会话数（便于单测断言）。 */
    public int runOnce() {
        return broadcaster.closeIdle(idleTimeoutMillis);
    }
}
