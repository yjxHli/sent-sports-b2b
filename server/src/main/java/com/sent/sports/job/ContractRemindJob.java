package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;

/**
 * 合同到期提醒（P5-43）：用 {@link JdbcTemplate} 扫描 V7 {@code contract} 表未来 30 天内到期的合同，
 * 返回条数并打日志，只引用真实列 {@code expire_date}（DATE）。
 * <p>窗口口径 {@code DATEDIFF(expire_date, 今天) BETWEEN 0 AND 30}，即今天起 30 天内到期；
 * 纯只读统计、不写数据，天然幂等、无需分布式锁。
 * <p>单轮查询若抛 {@link BizException} 只记录并跳过本轮，不冒泡到调度器，保证「不中断整轮」。
 */
@Component
public class ContractRemindJob {

    private static final Logger log = LoggerFactory.getLogger(ContractRemindJob.class);
    private static final int REMIND_DAYS = 30;
    private static final String COUNT_EXPIRING_SQL =
            "SELECT COUNT(*) FROM contract WHERE expire_date IS NOT NULL "
                    + "AND DATEDIFF(expire_date, ?) BETWEEN 0 AND " + REMIND_DAYS;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public ContractRemindJob(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    /** 每分钟扫一轮。 */
    @Scheduled(fixedDelayString = "${app.job.contract-remind-ms:60000}")
    public void scheduled() {
        runOnce();
    }

    /** 统计 30 天内到期合同数并写日志，返回统计条数（便于单测断言）。 */
    public int runOnce() {
        try {
            Date today = Date.valueOf(LocalDate.now(clock));
            int count = jdbcTemplate.queryForObject(COUNT_EXPIRING_SQL, Integer.class, today);
            if (count > 0) {
                log.info("合同到期提醒：未来 {} 天内共 {} 份合同到期", REMIND_DAYS, count);
            }
            return count;
        } catch (BizException e) {
            log.debug("合同到期提醒被业务异常打断，跳过本轮：{}", e.getMessage());
            return 0;
        }
    }
}
