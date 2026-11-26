package com.sent.sports.job;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customize.service.CustomDraftTimeoutQueryService;
import com.sent.sports.customize.service.CustomDraftTimeoutUseCase;
import com.sent.sports.customize.service.dto.CustomDraftTimeoutAction;
import com.sent.sports.customize.service.dto.CustomDraftWaiting;
import com.sent.sports.notify.service.MessageSendService;
import com.sent.sports.notify.service.dto.InboxMessage;
import com.sent.sports.order.service.CloseOrderUseCase;
import com.sent.sports.order.service.OrderQueryService;
import com.sent.sports.order.service.dto.CloseOrderCmd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 定制图稿上传超时（2026-11-23 用户口径）。
 *
 * <p>规则：订单支付后 72 小时仍未上传图稿 → 整单自动取消并全额退款（不走人工审核），
 * 抵扣的积分与优惠券一并返还；期间每 12 小时发一次站内信，文案带准确剩余时间。</p>
 *
 * <p>为什么编排放在 job 层：取消定制单在 customize、退款关单在 order、发信在 notify，
 * 三者各自有事务边界；若让 customize 直接调 order 会与 refund → customize 的门面形成依赖环，
 * 放在顶层的定时任务里串起来最干净（与 OrderTimeoutJob 编排 order + stock 的做法一致）。</p>
 *
 * <p>幂等：取消走 {@code WAIT_DRAFT -> CANCELLED} 条件更新，退款走订单侧状态机 + 条件更新，
 * 重复执行只会得到 STATE_CONFLICT 并跳过；提醒靠 {@code last_draft_remind_at} 条件更新抢占。</p>
 */
@Component
public class CustomDraftTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(CustomDraftTimeoutJob.class);
    private static final int BATCH_SIZE = 200;

    /** 关单原因会写进 {@code sys_operation_log.reason} 与退款单原因，必须能独立读懂。 */
    static final String CANCEL_REASON = "定制图稿 72 小时未上传，系统自动取消订单并全额退款";

    private final CustomDraftTimeoutQueryService queryService;
    private final CustomDraftTimeoutUseCase timeoutUseCase;
    private final CloseOrderUseCase closeOrderUseCase;
    /** 关单失败后回查订单真实终态：区分「已退款」与「根本关不掉」，两者的处置完全相反。 */
    private final OrderQueryService orderQueryService;
    private final MessageSendService messageSendService;
    private final Clock clock;
    /**
     * 存量豁免线：规则上线前支付的老单不追溯取消。
     * <p>没有这条线，任务首次执行会把历史库里所有「已支付且从未传稿」的定制单一次性取消退款
     * ——在演示库就是几十张单当场作废。需要全量追溯时把它调到 {@code 1970-01-01T00:00:00} 即可。</p>
     */
    private final LocalDateTime enforceFrom;

    public CustomDraftTimeoutJob(CustomDraftTimeoutQueryService queryService,
                                 CustomDraftTimeoutUseCase timeoutUseCase,
                                 CloseOrderUseCase closeOrderUseCase,
                                 OrderQueryService orderQueryService,
                                 MessageSendService messageSendService,
                                 Clock clock,
                                 @Value("${app.custom-draft.enforce-from:1970-01-01T00:00:00}") String enforceFrom) {
        this.queryService = queryService;
        this.timeoutUseCase = timeoutUseCase;
        this.closeOrderUseCase = closeOrderUseCase;
        this.orderQueryService = orderQueryService;
        this.messageSendService = messageSendService;
        this.clock = clock;
        this.enforceFrom = LocalDateTime.parse(enforceFrom.trim());
    }

    /** 每 5 分钟一轮（判定精度到小时，扫太勤只是白读库）。 */
    @Scheduled(fixedDelayString = "${app.job.custom-draft-timeout-ms:300000}")
    public void scheduled() {
        RunResult result = runOnce();
        if (result.cancelled() > 0 || result.reminded() > 0) {
            log.info("定制图稿超时任务：取消 {} 单，提醒 {} 单，跳过 {} 单",
                    result.cancelled(), result.reminded(), result.skipped());
        }
    }

    /** 执行一轮，返回处理计数（便于单测断言）。 */
    public RunResult runOnce() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<CustomDraftWaiting> waiting = queryService.findWaitingDrafts(enforceFrom, BATCH_SIZE);
        int cancelled = 0;
        int reminded = 0;
        int skipped = 0;
        for (CustomDraftWaiting item : waiting) {
            try {
                CustomDraftTimeoutAction action = item.decide(now);
                if (action == CustomDraftTimeoutAction.CANCEL) {
                    if (cancelAndRefund(item)) {
                        cancelled++;
                    } else {
                        skipped++;
                    }
                } else if (action == CustomDraftTimeoutAction.REMIND && remind(item, now)) {
                    reminded++;
                }
            } catch (RuntimeException e) {
                // 单条失败不能拖垮整轮：下一轮（5 分钟后）会重新扫到它
                skipped++;
                log.warn("定制图稿超时处理失败 customNo={}：{}", item.customNo(), e.getMessage());
            }
        }
        return new RunResult(cancelled, reminded, skipped);
    }

    /**
     * 先关单退款，再取消定制单。
     * <p>顺序不能反：钱没退成功就把定制单置成已取消，会让用户拿着「已取消但不退款」的单来投诉；
     * 反过来若关单成功而定制单取消失败，下一轮会因订单已关闭而走 STATE_CONFLICT 分支，
     * 继续把定制单补成取消——两种失败都能自愈。</p>
     */
    private boolean cancelAndRefund(CustomDraftWaiting item) {
        try {
            closeOrderUseCase.close(new CloseOrderCmd(item.orderNo(), CANCEL_REASON, null));
        } catch (BizException e) {
            if (e.getErrorCode() != ErrorCode.STATE_CONFLICT) {
                // 网关拒绝 / 金额异常：这一轮不动定制单，留给下一轮重试
                log.warn("订单 {} 自动退款失败，保留定制单待重试：{}", item.orderNo(), e.resolvedMessage());
                return false;
            }
            // 状态冲突有两种含义，必须回查真实终态才能区分：
            //   1) 订单已被关掉并全额退款（上一轮成功、本轮重扫）→ 只需把定制单补成取消；
            //   2) 订单已完成 / 售后中，状态机不允许再关单 → 绝不能取消定制单，否则「单没了钱没退」。
            if (!orderQueryService.isClosedWithFullRefund(item.orderNo())) {
                log.warn("订单 {} 无法自动关单（状态冲突且未全额退款），保留定制单待人工处理", item.orderNo());
                return false;
            }
            log.debug("订单 {} 已关闭并全额退款，仅补取消定制单", item.orderNo());
        }
        boolean cancelled = timeoutUseCase.cancelOverdue(item.customNo());
        if (cancelled) {
            notifyCancelled(item);
        }
        return cancelled;
    }

    /** 抢占提醒资格成功才发信，避免调度重入或错过补发导致连发。 */
    private boolean remind(CustomDraftWaiting item, LocalDateTime now) {
        if (!timeoutUseCase.claimReminder(item.customNo(), now)) {
            return false;
        }
        String remaining = item.remainingText(now);
        String content = "您的定制单 " + item.customNo() + "（订单 " + item.orderNo()
                + "）还未上传设计图稿，剩余 " + remaining + " 可上传。"
                + "逾期未上传，系统将自动取消订单并原路退款，抵扣的积分与优惠券会一并返还。"
                + "请尽快在「我的定制」中上传设计稿。";
        try {
            messageSendService.send(new InboxMessage(item.customerId(), "ORDER", "定制图稿待上传提醒",
                    content, "CUSTOM", item.customNo()));
        } catch (RuntimeException e) {
            // 提醒时间已推进，本轮不再重试；下一轮（12 小时后）会再提醒，不制造重复消息
            log.warn("定制图稿提醒发送失败 customNo={}：{}", item.customNo(), e.getMessage());
        }
        return true;
    }

    private void notifyCancelled(CustomDraftWaiting item) {
        String content = "定制单 " + item.customNo() + "（订单 " + item.orderNo()
                + "）在支付后 72 小时内未上传设计图稿，系统已自动取消订单并全额退款。"
                + "退款将原路退回，抵扣的积分与使用的优惠券已返还账户。";
        try {
            messageSendService.send(new InboxMessage(item.customerId(), "ORDER", "定制单已自动取消",
                    content, "CUSTOM", item.customNo()));
        } catch (RuntimeException e) {
            log.warn("定制单取消通知发送失败 customNo={}：{}", item.customNo(), e.getMessage());
        }
    }

    /** 一轮处理结果：取消息数 / 提醒数 / 跳过数。 */
    public record RunResult(int cancelled, int reminded, int skipped) {
    }
}
