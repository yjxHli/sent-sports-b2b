package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.order.mapper.OrderMapper;
import com.sent.sports.order.mapper.po.OrderPO;
import com.sent.sports.order.service.CancelOrderUseCase;
import com.sent.sports.order.service.dto.CancelOrderCmd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时取消（P5-39，验收 A1）：扫描已过支付截止时间仍未支付的订单，逐单走取消用例。
 * <ul>
 *   <li><b>幂等</b>：取消本身是条件迁移（仅 WAIT_PAY 可取消），被并发处理过的单会抛
 *       {@code STATE_CONFLICT}，此处吞掉并继续——重复执行不会产生副作用，不需要分布式锁；</li>
 *   <li><b>批量上限</b>：每轮最多处理 {@value #BATCH_SIZE} 单，避免长时间占用单线程调度器。</li>
 * </ul>
 */
@Component
public class OrderTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutJob.class);
    private static final int BATCH_SIZE = 200;

    private final OrderMapper orderMapper;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final Clock clock;

    public OrderTimeoutJob(OrderMapper orderMapper, CancelOrderUseCase cancelOrderUseCase, Clock clock) {
        this.orderMapper = orderMapper;
        this.cancelOrderUseCase = cancelOrderUseCase;
        this.clock = clock;
    }

    /** 每分钟扫一轮。 */
    @Scheduled(fixedDelayString = "${app.job.order-timeout-ms:60000}")
    public void scheduled() {
        int cancelled = cancelTimeoutOrders();
        if (cancelled > 0) {
            log.info("订单超时取消：本轮取消 {} 单", cancelled);
        }
    }

    /** 执行一轮取消，返回成功取消的订单数（便于单测断言）。 */
    public int cancelTimeoutOrders() {
        List<OrderPO> timeoutOrders = orderMapper.selectTimeoutUnpaid(LocalDateTime.now(clock), BATCH_SIZE);
        int cancelled = 0;
        for (OrderPO order : timeoutOrders) {
            try {
                cancelOrderUseCase.cancel(new CancelOrderCmd(order.getOrderNo()));
                cancelled++;
            } catch (BizException e) {
                // 已被用户主动取消或支付：属于正常竞争结果，跳过
                log.debug("跳过订单 {}：{}", order.getOrderNo(), e.getMessage());
            }
        }
        return cancelled;
    }
}
