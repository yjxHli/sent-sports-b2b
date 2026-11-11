package com.sent.sports.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;

/** 调度配置单测（P5-38）：单线程 + 优雅停机 + 异常不打断调度。 */
class ScheduleConfigTest {

    @Test
    @DisplayName("任务调度器为单线程并配好线程名前缀")
    void schedulerIsSingleThreaded() {
        ThreadPoolTaskScheduler scheduler = new ScheduleConfig().taskScheduler();
        scheduler.initialize();
        try {
            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(1);
            assertThat(scheduler.getThreadNamePrefix()).isEqualTo("sent-job-");
        } finally {
            scheduler.shutdown();
        }
    }
}
