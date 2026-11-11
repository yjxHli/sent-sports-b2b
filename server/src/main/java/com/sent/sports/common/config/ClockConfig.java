package com.sent.sports.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 时钟装配（P5）：全应用统一注入 {@link Clock}，避免各处直接 {@code System.currentTimeMillis()}。
 * <p>好处：① 业务代码里的「现在」可被单测替换为固定时钟；② 时区统一按 {@code Asia/Shanghai}（与 Jackson 配置一致），
 * 不会出现「本机时区不同导致定时任务算错截止时间」的问题。
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(java.time.ZoneId.of("Asia/Shanghai"));
    }
}
