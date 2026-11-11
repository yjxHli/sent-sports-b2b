package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.refund.mapper.RefundOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * SLA 升级提醒（P5-47）：汇总「审图超时的定制单」与「待审核退款单」两类待办数量并打日志。
 * <p>纯只读统计、不写数据，天然幂等、无需分布式锁；两路查询各自独立，
 * 任一查询抛 {@link BizException} 只跳过该路、不影响另一路汇总，保证「不中断整轮」。
 */
@Component
public class SlaRemindJob {

    private static final Logger log = LoggerFactory.getLogger(SlaRemindJob.class);
    private static final int BATCH_SIZE = 200;

    private final CustomOrderMapper customOrderMapper;
    private final RefundOrderMapper refundOrderMapper;
    private final Clock clock;

    public SlaRemindJob(CustomOrderMapper customOrderMapper, RefundOrderMapper refundOrderMapper, Clock clock) {
        this.customOrderMapper = customOrderMapper;
        this.refundOrderMapper = refundOrderMapper;
        this.clock = clock;
    }

    /** 每分钟扫一轮。 */
    @Scheduled(fixedDelayString = "${app.job.sla-remind-ms:60000}")
    public void scheduled() {
        runOnce();
    }

    /** 汇总两路 SLA 待办总数并写日志，返回总数（便于单测断言）。 */
    public int runOnce() {
        LocalDateTime now = LocalDateTime.now(clock);

        int reviewTimeout = 0;
        try {
            reviewTimeout = customOrderMapper.selectReviewTimeout(now, BATCH_SIZE).size();
        } catch (BizException e) {
            log.debug("审图超时查询跳过：{}", e.getMessage());
        }

        int pendingRefund = 0;
        try {
            pendingRefund = refundOrderMapper.selectPending(BATCH_SIZE).size();
        } catch (BizException e) {
            log.debug("待审退款查询跳过：{}", e.getMessage());
        }

        int total = reviewTimeout + pendingRefund;
        if (total > 0) {
            log.info("SLA 提醒：审图超时 {} 单，待审退款 {} 单，合计 {}", reviewTimeout, pendingRefund, total);
        }
        return total;
    }
}
