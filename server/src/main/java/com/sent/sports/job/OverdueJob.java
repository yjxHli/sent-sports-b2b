package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.finance.service.ReceivableJobService;
import com.sent.sports.finance.service.dto.OverdueReceivable;
import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.notify.service.dto.InboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * 应收逾期重算（P5-41）：以「未收满 + 已过期」为口径扫描逾期应收，把状态刷成 OVERDUE。
 * <ul>
 *   <li><b>幂等</b>：已是 OVERDUE 的行直接跳过，重复执行只做一次状态写入；无需分布式锁；</li>
 *   <li><b>不中断整轮</b>：单笔更新抛 {@link BizException}（如 STATE_CONFLICT）只跳过该笔，继续处理其余应收；</li>
 *   <li><b>站内信</b>：仅当 {@code markOverdue} 实际更新了行才通知客户，避免并发重复提醒。</li>
 * </ul>
 */
@Component
public class OverdueJob {

    private static final Logger log = LoggerFactory.getLogger(OverdueJob.class);
    private final ReceivableJobService receivableJobService;
    private final MessageSendService messageSendService;
    private final Clock clock;

    public OverdueJob(ReceivableJobService receivableJobService, MessageSendService messageSendService,
                      Clock clock) {
        this.receivableJobService = receivableJobService;
        this.messageSendService = messageSendService;
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
        // 已置 OVERDUE 的行由 finance 服务过滤，任务只负责逐笔触发并通知
        List<OverdueReceivable> overdue = receivableJobService.findOverdueDetails(LocalDate.now(clock));
        int updated = 0;
        for (OverdueReceivable item : overdue) {
            try {
                if (receivableJobService.markOverdue(item.receivableId()) > 0) {
                    updated++;
                    notifyCustomer(item);
                }
            } catch (BizException e) {
                log.debug("跳过应收 {}：{}", item.receivableId(), e.getMessage());
            }
        }
        return updated;
    }

    /** 站内信随「置逾期」成功才发，收件人无效时由消息服务自身跳过。 */
    private void notifyCustomer(OverdueReceivable item) {
        String amount = String.format(Locale.CHINA, "%,.2f", item.amountCent() / 100.0);
        messageSendService.send(new InboxMessage(
                item.customerId(),
                "SYSTEM",
                "应收逾期提醒",
                "订单 " + item.orderNo() + " 的应收 ¥" + amount
                        + " 已于 " + item.dueDate() + " 到期，请尽快完成回款。",
                "ORDER",
                item.orderNo()));
    }
}
