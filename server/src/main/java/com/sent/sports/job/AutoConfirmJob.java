package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.order.service.ConfirmReceiptUseCase;
import com.sent.sports.order.service.OrderJobQueryService;
import com.sent.sports.order.service.dto.ConfirmReceiptCmd;
import com.sent.sports.order.service.dto.ConfirmReceiptResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 已送达后自动确认收货（P5-40 / 2026-11-17 用户要求收紧到 7 天）：
 * 扫描最后更新（发货/送达回写）超过 7 天仍为 WAIT_RECEIVE 的订单，交给
 * {@link ConfirmReceiptUseCase} 完成「收货 → 订单完成 → 发成长值/积分 → 生成应收」。
 *
 * <p><b>P0 修复（审计 R1）</b>：本任务原先直接调 {@code OrderMapper.transitStatus} 做条件更新，
 * 只推进状态、不触发任何下游副作用——7 天未确认的订单因此永远拿不到积分与成长值，
 * 财务也永远收不到应收台账（且 {@code uk_receivable_order} 会让事后补数变成人工对账）。
 * 现在「同一业务动作只有一个入口」：Job 只负责发现到期项，动作全部委托用例。</p>
 *
 * <ul>
 *   <li><b>幂等</b>：用例侧仍是「WAIT_RECEIVE 才可确认」的状态准入，重复执行不会二次发奖励；</li>
 *   <li><b>批量上限</b>：每轮最多 {@value #BATCH_SIZE} 单，避免长时间占用单线程调度器；</li>
 *   <li><b>不中断整轮</b>：单笔抛 {@link BizException}（如 STATE_CONFLICT、库存/台账异常）
 *       只跳过该单，继续处理其余订单，下一轮会重新扫描到未成功的订单。</li>
 * </ul>
 */
@Component
public class AutoConfirmJob {

    private static final Logger log = LoggerFactory.getLogger(AutoConfirmJob.class);
    private static final int BATCH_SIZE = 200;
    /** 已送达/发货后未确认收货的自动确认阈值（用户要求：超过 7 天自动收货）。 */
    public static final int AUTO_CONFIRM_DAYS = 7;

    private final OrderJobQueryService orderJobQueryService;
    private final ConfirmReceiptUseCase confirmReceiptUseCase;
    private final Clock clock;

    public AutoConfirmJob(OrderJobQueryService orderJobQueryService, ConfirmReceiptUseCase confirmReceiptUseCase,
                          Clock clock) {
        this.orderJobQueryService = orderJobQueryService;
        this.confirmReceiptUseCase = confirmReceiptUseCase;
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

    /** 执行一轮自动确认，返回成功完成的订单数（便于单测断言）。 */
    public int runOnce() {
        LocalDateTime before = LocalDateTime.now(clock).minusDays(AUTO_CONFIRM_DAYS);
        List<String> orderNos = orderJobQueryService.findAwaitingReceiveOrderNos(before, BATCH_SIZE);
        int confirmed = 0;
        for (String orderNo : orderNos) {
            try {
                // 空明细 = 「已发未收」逐行全量确认（与用户端点「确认收货」按钮同一路径）
                ConfirmReceiptResult result = confirmReceiptUseCase.confirm(
                        new ConfirmReceiptCmd(orderNo, List.of()));
                if (result.completed()) {
                    confirmed++;
                } else {
                    // 仍有未发/未收的行：用例保持 WAIT_RECEIVE，下一轮继续跟进
                    log.debug("订单 {} 尚未全部收货，本轮不完成", orderNo);
                }
            } catch (BizException e) {
                // 并发下已被用户确认、或订单已进入售后：按冲突跳过，不影响其余订单
                log.debug("跳过订单 {}：{}", orderNo, e.resolvedMessage());
            }
        }
        return confirmed;
    }
}
