package com.sent.sports.job;

import com.sent.sports.product.service.FootprintService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 足迹过期清理（2026-11-28 用户要求：我的足迹只保留一个月）。
 *
 * <p>为什么要有任务而不是只在读接口里过滤：过滤只能「看不见」，记录会一直堆在库里越滚越大。
 * 所以读接口按同一时间窗过滤（过期即不可见），这个任务每天把过期的物理删掉。</p>
 *
 * <p>幂等：删除条件完全由时间决定，重复执行只是删 0 行；没有分布式锁也不会重复计数，
 * 删除行数只用于日志（0 行不打日志，避免每天一条噪声）。</p>
 */
@Component
public class FootprintCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(FootprintCleanupJob.class);

    private final FootprintService footprintService;

    public FootprintCleanupJob(FootprintService footprintService) {
        this.footprintService = footprintService;
    }

    /** 每天 03:20 清一轮（低峰，且不与 03:00 的对账任务撞车）。 */
    @Scheduled(cron = "${app.job.footprint-cleanup-cron:0 20 3 * * ?}")
    public void scheduled() {
        runOnce();
    }

    /**
     * 执行一次清理，返回删除行数。
     * <p>独立成公开方法便于单测直接调用，不必等定时触发（与 {@code OverdueJob#runOnce} 同一口径）。</p>
     */
    public int runOnce() {
        int deleted = footprintService.cleanupExpired();
        if (deleted > 0) {
            log.info("足迹过期清理：删除 {} 条超过一个月的浏览记录", deleted);
        }
        return deleted;
    }
}
