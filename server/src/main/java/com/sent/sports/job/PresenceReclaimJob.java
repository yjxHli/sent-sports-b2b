package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 坐席在线状态回收（P5-46）：把心跳超时的 ONLINE 坐席置为 OFFLINE 并清零活跃会话数。
 * <p>SQL 为条件更新（{@code WHERE status='ONLINE' AND last_heartbeat_at <= ?}），
 * 已被并发回收的行不再命中，重复执行不产生副作用，天然幂等、无需分布式锁。
 * <p>心跳超时阈值默认 60 秒，可由 {@code app.job.presence-heartbeat-timeout-ms} 覆盖；
 * 单轮更新若抛 {@link BizException} 只跳过本轮，不中断整轮。
 */
@Component
public class PresenceReclaimJob {

    private static final Logger log = LoggerFactory.getLogger(PresenceReclaimJob.class);
    private static final long DEFAULT_TIMEOUT_MILLIS = 60_000L;
    private static final String RECLAIM_SQL =
            "UPDATE staff_presence SET status = 'OFFLINE', active_sessions = 0 "
                    + "WHERE status = 'ONLINE' AND last_heartbeat_at <= ?";

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final long heartbeatTimeoutMillis;

    /** 单测/默认构造：60 秒阈值。 */
    public PresenceReclaimJob(JdbcTemplate jdbcTemplate, Clock clock) {
        this(jdbcTemplate, clock, DEFAULT_TIMEOUT_MILLIS);
    }

    @Autowired
    public PresenceReclaimJob(JdbcTemplate jdbcTemplate, Clock clock,
                              @Value("${app.job.presence-heartbeat-timeout-ms:60000}") long heartbeatTimeoutMillis) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.heartbeatTimeoutMillis = heartbeatTimeoutMillis > 0
                ? heartbeatTimeoutMillis : DEFAULT_TIMEOUT_MILLIS;
    }

    /** 每分钟扫一轮。 */
    @Scheduled(fixedDelayString = "${app.job.presence-reclaim-ms:60000}")
    public void scheduled() {
        int reclaimed = runOnce();
        if (reclaimed > 0) {
            log.info("坐席在线回收：本轮回收 {} 个坐席", reclaimed);
        }
    }

    /** 执行一轮回收，返回受影响行数（便于单测断言）。 */
    public int runOnce() {
        try {
            Timestamp cutoff = Timestamp.valueOf(
                    LocalDateTime.now(clock).minus(Duration.ofMillis(heartbeatTimeoutMillis)));
            return jdbcTemplate.update(RECLAIM_SQL, cutoff);
        } catch (BizException e) {
            log.debug("坐席在线回收被业务异常打断，跳过本轮：{}", e.getMessage());
            return 0;
        }
    }
}
