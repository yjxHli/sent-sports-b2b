package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.finance.mapper.ReceivableMapper;
import com.sent.sports.finance.mapper.po.ReceivablePO;
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
    private static final String OVERDUE = "OVERDUE";

    private final ReceivableMapper receivableMapper;
    private final Clock clock;

    public OverdueJob(ReceivableMapper receivableMapper, Clock clock) {
        this.receivableMapper = receivableMapper;
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
        List<ReceivablePO> receivables = receivableMapper.selectOverdue(LocalDate.now(clock));
        int updated = 0;
        for (ReceivablePO receivable : receivables) {
            if (OVERDUE.equals(receivable.getStatus())) {
                // 已置过：跳过，保证重复执行不产生额外写入
                continue;
            }
            try {
                if (receivableMapper.updateStatus(receivable.getId(), OVERDUE) > 0) {
                    updated++;
                }
            } catch (BizException e) {
                log.debug("跳过应收 {}：{}", receivable.getId(), e.getMessage());
            }
        }
        return updated;
    }
}
