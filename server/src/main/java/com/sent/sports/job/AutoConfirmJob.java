package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderPO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 发货后自动确认收货（P5-40）：扫描发货超过 15 天仍为 WAIT_RECEIVE 的订单，条件迁移到 COMPLETED。
 * <ul>
 *   <li><b>幂等</b>：{@code transitStatus} 是「WHERE status = 期望旧值」的条件更新，返回 0 行即表示
 *       并发下已被他人确认/售后推进，按冲突跳过；重复执行不会二次迁移，无需分布式锁；</li>
 *   <li><b>批量上限</b>：每轮最多 {@value #BATCH_SIZE} 单，避免长时间占用单线程调度器；</li>
 *   <li><b>不中断整轮</b>：单笔抛 {@link BizException}（如 STATE_CONFLICT）只跳过该单，继续处理其余订单。</li>
 * </ul>
 */
@Component
public class AutoConfirmJob {

    private static final Logger log = LoggerFactory.getLogger(AutoConfirmJob.class);
    private static final int BATCH_SIZE = 200;
    private static final int AUTO_CONFIRM_DAYS = 15;
    private static final String FROM = "WAIT_RECEIVE";
    private static final String TO = "COMPLETED";

    private final OrderMapper orderMapper;
    private final Clock clock;

    public AutoConfirmJob(OrderMapper orderMapper, Clock clock) {
        this.orderMapper = orderMapper;
        this.clock = clock;
    }

    /** 每分钟扫一轮。 */
    @Scheduled(fixedDelayString = "${app.job.auto-confirm-ms:60000}")
    public void scheduled() {
        int confirmed = runOnce();
        if (confirmed > 0) {
            log.info("订单自动确认收货：本轮确认 {} 单", confirmed);
        }
    }

    /** 执行一轮自动确认，返回成功迁移的订单数（便于单测断言）。 */
    public int runOnce() {
        LocalDateTime before = LocalDateTime.now(clock).minusDays(AUTO_CONFIRM_DAYS);
        List<OrderPO> orders = orderMapper.selectAwaitingReceiveBefore(before, BATCH_SIZE);
        int confirmed = 0;
        for (OrderPO order : orders) {
            try {
                int rows = orderMapper.transitStatus(order.getOrderNo(), FROM, TO);
                if (rows > 0) {
                    confirmed++;
                } else {
                    // 条件更新未命中：并发下已被推进，属正常竞争结果
                    log.debug("跳过订单 {}：条件更新未命中（已被并发处理）", order.getOrderNo());
                }
            } catch (BizException e) {
                log.debug("跳过订单 {}：{}", order.getOrderNo(), e.getMessage());
            }
        }
        return confirmed;
    }
}
