package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.finance.service.ReceivableJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 应收逾期重算（P5-41）：以「未收满 + 已过期」为口径扫描逾期应收，把状态刷成 OVERDUE。
 * <ul>
 *   <li><b>幂等</b>：已是 OVERDUE 的行直接跳过，重复执行只做一次状态写入；无需分布式锁；</li>
 *   <li><b>不中断整轮</b>：单笔更新抛 {@link BizException}（如 STATE_CONFLICT）只跳过该笔，继续处理其余应收。</li>
 * </ul>
 */
@Component
public class OverdueJob {

    private static final Logger log = LoggerFactory.getLogger(OverdueJob.class);
    private final ReceivableJobService receivableJobService;
    private final Clock clock;

    public OverdueJob(ReceivableJobService receivableJobService, Clock clock) {
        this.receivableJobService = receivableJobService;
        this.clock = clock;
    }

    /** 每分钟扫一轮。 */
    @Scheduled(fixedDelayString = "${app.job.overdue-ms:60000}")
    public void scheduled() {
        int updated = runOnce();
        if (updated > 0) {
            log.info("应收逾期重算：本轮置为 OVERDUE {} 笔", updated);
        }
    }

    /** 执行一轮逾期重算，返回成功更新的应收笔数（便于单测断言）。 */
    public int runOnce() {
        // 已置 OVERDUE 的行由 finance 服务过滤，任务只负责逐笔触发
        List<Long> receivableIds = receivableJobService.findOverdueIds(LocalDate.now(clock));
        int updated = 0;
        for (Long receivableId : receivableIds) {
            try {
                if (receivableJobService.markOverdue(receivableId) > 0) {
                    updated++;
                }
            } catch (BizException e) {
                log.debug("跳过应收 {}：{}", receivableId, e.getMessage());
            }
        }
        return updated;
    }
}
