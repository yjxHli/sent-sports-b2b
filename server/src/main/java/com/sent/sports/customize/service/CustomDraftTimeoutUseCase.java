package com.sent.sports.customize.service;

import com.sent.sports.common.anno.UseCase;
import com.sent.sports.customize.domain.CustomDraftTimeoutPolicy;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 图稿上传超时的两件写操作：超时取消、提醒落痕。
 *
 * <p>都不返回值给前端，只给定时任务用；两者都是<b>条件更新</b>，由数据库判定「还能不能改」，
 * 因此天然幂等——多实例/重复执行不会二次取消，也不会重复发信（不需要分布式锁）。</p>
 */
@Service
public class CustomDraftTimeoutUseCase {

    private final CustomOrderMapper customOrderMapper;

    public CustomDraftTimeoutUseCase(CustomOrderMapper customOrderMapper) {
        this.customOrderMapper = customOrderMapper;
    }

    /**
     * 超时取消定制单：{@code WAIT_DRAFT -> CANCELLED}。
     *
     * @return true = 本次真的取消；false = 状态已被并发推进（已传图稿 / 已取消），调用方不必补偿
     */
    @UseCase
    public boolean cancelOverdue(String customNo) {
        return customOrderMapper.transitStatus(customNo, "WAIT_DRAFT", "CANCELLED") > 0;
    }

    /**
     * 抢占本轮提醒资格：只有把 {@code last_draft_remind_at} 推进成功的那一轮才发站内信。
     * <p>条件里带 {@code threshold = now - 12h}：即便调度器因为重启错过了一轮，
     * 也不会把欠下的几条提醒一次性补发出来轰炸用户。</p>
     *
     * @return true = 抢到资格，可以发信
     */
    public boolean claimReminder(String customNo, LocalDateTime now) {
        LocalDateTime threshold = now.minusHours(CustomDraftTimeoutPolicy.REMIND_INTERVAL_HOURS);
        return customOrderMapper.markDraftReminded(customNo, now, threshold) > 0;
    }
}
