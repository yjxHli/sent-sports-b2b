package com.sent.sports.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 调度配置（P5-38）：单线程执行所有定时任务。
 * <p>刻意只给 1 个线程：本期是单实例部署，任务之间串行可避免「取消订单」与「自动收货」同时改同一张表；
 * 吞吐不是瓶颈，正确性优先。
 * <p><b>分布式扩展点</b>：多实例部署时不要加大线程数，而应把「谁执行」交给外部协调（如数据库行锁抢占
 * 或独立调度服务），每个任务内部已是条件更新 + 唯一键，重复执行的副作用可控。
 */
@Configuration
@EnableScheduling
public class ScheduleConfig {

    private static final Logger log = LoggerFactory.getLogger(ScheduleConfig.class);

    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("sent-job-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.setErrorHandler(throwable -> log.error("定时任务执行异常", throwable));
        return scheduler;
    }
}
