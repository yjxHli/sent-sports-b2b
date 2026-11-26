package com.sent.sports.customize.service.dto;

import com.sent.sports.customize.domain.CustomDraftTimeoutPolicy;

import java.time.LocalDateTime;

/**
 * 待上传图稿的定制单（超时任务读模型，跨层中立 DTO：不含 PO / mapper 类型）。
 *
 * @param customNo     定制单号
 * @param orderNo      关联订单号（整单取消与站内信跳转都用它）
 * @param customerId   买家（站内信收件人）
 * @param paidAt       订单支付时间——72 小时倒计时的起算点
 * @param remindCount  已发提醒次数（仅用于日志排查）
 * @param lastRemindAt 最近一次提醒时间（null = 从未提醒）
 */
public record CustomDraftWaiting(String customNo, String orderNo, long customerId,
                                 LocalDateTime paidAt, int remindCount, LocalDateTime lastRemindAt) {

    /** 上传截止时刻（支付 + 72 小时）。 */
    public LocalDateTime deadline() {
        return CustomDraftTimeoutPolicy.deadlineOf(paidAt);
    }

    /**
     * 本轮该做什么：超时取消 / 发提醒 / 什么都不做。
     *
     * <p>判定放在这里而不是定时任务里，一是因为「72 小时、12 小时」是定制模块的业务口径，
     * 二是因为架构规则不允许 {@code job} 直接依赖别的模块的 {@code domain}
     * （{@code CustomDraftTimeoutPolicy} 属于 customize.domain）——任务只做编排。</p>
     */
    public CustomDraftTimeoutAction decide(LocalDateTime now) {
        if (CustomDraftTimeoutPolicy.isOverdue(paidAt, now)) {
            return CustomDraftTimeoutAction.CANCEL;
        }
        return CustomDraftTimeoutPolicy.shouldRemind(paidAt, lastRemindAt, now)
                ? CustomDraftTimeoutAction.REMIND
                : CustomDraftTimeoutAction.NONE;
    }

    /** 站内信正文里的「剩余 X」文案，口径与 {@link #decide} 同源。 */
    public String remainingText(LocalDateTime now) {
        return CustomDraftTimeoutPolicy.remainingText(deadline(), now);
    }
}
