package com.sent.sports.customize.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 定制图稿上传时限策略（纯 Java，无 Spring 依赖，2026-11-23 用户口径）。
 *
 * <p>规则：订单<b>支付成功</b>起 72 小时内必须上传图稿，逾期系统自动取消订单并全额退款；
 * 期间每 12 小时发一次站内信提醒，并告知准确剩余时间。</p>
 *
 * <p>为什么起算点是支付时间而不是下单时间：定制单在下单时就落 {@code WAIT_DRAFT}
 * （见 {@code CreateOrderUseCase}），但未支付订单由 {@code OrderTimeoutJob} 单独取消，
 * 若这里也按下单时间算，同一张单会被两条链路各取消一次，退款口径也会打对折。</p>
 *
 * <p>为什么首条提醒在支付后 12 小时而不是立即：这条链路是新单的「催办」而不是「回执」，
 * 用户刚付完款就收到催上传只会被当成骚扰；12 小时给足自然操作时间。</p>
 */
public final class CustomDraftTimeoutPolicy {

    /** 支付后允许上传图稿的时限（小时）。 */
    public static final int DEADLINE_HOURS = 72;

    /** 站内信提醒间隔（小时）。 */
    public static final int REMIND_INTERVAL_HOURS = 12;

    private CustomDraftTimeoutPolicy() {
    }

    /** 上传截止时刻 = 支付时刻 + 72 小时。 */
    public static LocalDateTime deadlineOf(LocalDateTime paidAt) {
        return paidAt == null ? null : paidAt.plusHours(DEADLINE_HOURS);
    }

    /** 是否已过上传截止时刻（到点即取消，不需要额外宽限）。 */
    public static boolean isOverdue(LocalDateTime paidAt, LocalDateTime now) {
        LocalDateTime deadline = deadlineOf(paidAt);
        return deadline != null && now != null && !now.isBefore(deadline);
    }

    /**
     * 本轮是否该发提醒：已到首次提醒时点（支付后 12h），且距上次提醒满 12h，且尚未超时。
     * <p>超时后不再提醒——接下来要做的是取消并退款，不是继续催。</p>
     */
    public static boolean shouldRemind(LocalDateTime paidAt, LocalDateTime lastRemindAt, LocalDateTime now) {
        if (paidAt == null || now == null || isOverdue(paidAt, now)) {
            return false;
        }
        LocalDateTime baseline = lastRemindAt == null ? paidAt : lastRemindAt;
        return !now.isBefore(baseline.plusHours(REMIND_INTERVAL_HOURS));
    }

    /**
     * 剩余时间文案：{@code 2 天 3 小时} / {@code 5 小时 20 分钟} / {@code 23 分钟}。
     * <p>取到分钟即可——站内信不是倒计时器，秒级精度只会让文案抖动。</p>
     */
    public static String remainingText(LocalDateTime deadline, LocalDateTime now) {
        if (deadline == null || now == null || !now.isBefore(deadline)) {
            return "0 分钟";
        }
        long minutes = Duration.between(now, deadline).toMinutes();
        long days = minutes / (24 * 60);
        long hours = (minutes % (24 * 60)) / 60;
        long mins = minutes % 60;
        if (days > 0) {
            return hours > 0 ? days + " 天 " + hours + " 小时" : days + " 天";
        }
        if (hours > 0) {
            return mins > 0 ? hours + " 小时 " + mins + " 分钟" : hours + " 小时";
        }
        return mins + " 分钟";
    }
}
