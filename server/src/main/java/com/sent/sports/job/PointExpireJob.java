package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 积分过期统计（P5-42）：用 {@link JdbcTemplate} 扫描 V3 {@code point_log} 中已过期的流水并计数。
 * <p><b>为什么只统计 + 打日志、不做过期落账</b>：V3 {@code point_log} 仅有 {@code expire_at}
 * 可用来识别「哪些流水已过期」，但表内<b>没有</b>记录「是否已过期/是否已处理」的状态列，
 * 也<b>没有</b>回写积分余额或登记过期冲销流水的目标列。因此无法用「条件更新」把一条流水幂等地标记为已过期
 * （既不能置状态位，也不能生成冲销流水），任何 UPDATE 都无法判定「本轮是否已处理过」，也就不满足约定 3 的幂等前提。
 * 故按需求降级为<b>只读统计 + 写日志</b>：仅引用真实存在的 {@code expire_at} 列统计过期条数，
 * 不写任何数据、不编造列名，天然幂等、无需分布式锁。待后续迁移补齐状态/冲销列后，再改为条件更新式过期落账。
 * <p>单轮查询若抛 {@link BizException} 只记录并跳过本轮，不冒泡到调度器，保证「不中断整轮」。
 */
@Component
public class PointExpireJob {

    private static final Logger log = LoggerFactory.getLogger(PointExpireJob.class);
    private static final String COUNT_EXPIRED_SQL =
            "SELECT COUNT(*) FROM point_log WHERE expire_at IS NOT NULL AND expire_at <= ?";

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public PointExpireJob(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /** 每分钟扫一轮。 */
    @Scheduled(fixedDelayString = "${app.job.point-expire-ms:60000}")
    public void scheduled() {
        runOnce();
    }

    /** 统计已过期积分流水条数并写日志，返回统计条数（便于单测断言）。 */
    public int runOnce() {
        try {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now(clock));
            int expired = jdbcTemplate.queryForObject(COUNT_EXPIRED_SQL, Integer.class, now);
            if (expired > 0) {
                log.info("积分过期统计：截至 {} 共 {} 条 point_log 流水已过期（仅统计，未落账）", now, expired);
            }
            return expired;
        } catch (BizException e) {
            log.debug("积分过期统计被业务异常打断，跳过本轮：{}", e.getMessage());
            return 0;
        }
    }
}
