package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.finance.service.ContractJobService;
import com.sent.sports.finance.service.dto.ContractReminder;
import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.notify.service.dto.InboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

/**
 * 合同到期提醒（P5-43）：扫描未来 30 天内到期的合同，按「30 天 / 7 天 / 当天」三级发站内信。
 * <p><b>幂等</b>：先条件更新 {@code expire_remind_level}（V67），只有影响行数 &gt; 0 才发信；
 * 并发下被别的任务抢先不会重复打扰客户，无需分布式锁。</p>
 * <p><b>不中断整轮</b>：单笔更新抛 {@link BizException} 只跳过该笔，继续处理其余合同。</p>
 */
@Component
public class ContractRemindJob {

    private static final Logger log = LoggerFactory.getLogger(ContractRemindJob.class);

    private final ContractJobService contractJobService;
    private final MessageSendService messageSendService;
    private final Clock clock;

    public ContractRemindJob(ContractJobService contractJobService,
                             MessageSendService messageSendService,
                             Clock clock) {
        this.contractJobService = contractJobService;
        this.messageSendService = messageSendService;
        this.clock = clock;
    }

    /** 每分钟扫一轮。 */
    @Scheduled(fixedDelayString = "${app.job.contract-remind-ms:60000}")
    public void scheduled() {
        runOnce();
    }

    /** 执行一轮提醒，返回成功投递的合同数（便于单测断言）。 */
    public int runOnce() {
        LocalDate today = LocalDate.now(clock);
        try {
            List<ContractReminder> expiring = contractJobService.findExpiring(today);
            int notified = 0;
            for (ContractReminder item : expiring) {
                try {
                    if (contractJobService.markReminded(item.contractId(), item.remindLevel()) > 0) {
                        notifyCustomer(item, today);
                        notified++;
                    }
                } catch (BizException e) {
                    log.debug("跳过合同 {}：{}", item.contractId(), e.getMessage());
                }
            }
            if (notified > 0) {
                log.info("合同到期提醒：本轮通知 {} 份合同", notified);
            }
            return notified;
        } catch (BizException e) {
            log.debug("合同到期提醒被业务异常打断，跳过本轮：{}", e.getMessage());
            return 0;
        }
    }

    /** 站内信必须跟条件更新绑定：写入失败就绝不发信。 */
    private void notifyCustomer(ContractReminder item, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, item.expireDate());
        String deadline = days <= 0 ? "今天到期" : "还有 " + days + " 天到期";
        String amount = String.format(Locale.CHINA, "%,.2f", item.amountCent() / 100.0);
        String content = "合同 " + item.contractNo() + " 将于 " + item.expireDate()
                + " 到期（" + deadline + "），合同金额 ¥" + amount + "，请尽快续签。";
        messageSendService.send(new InboxMessage(item.customerId(), "SYSTEM", "合同到期提醒",
                content, "ORDER", item.contractNo()));
    }
}
