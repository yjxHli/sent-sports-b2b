package com.sent.sports.system.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时把 DB 词库灌进 {@code SensitiveWordFilter}（V46）。
 *
 * <p>顺序很关键：{@code SensitiveWordFilter} Bean 初始化时用的是 classpath 词库（兜底），
 * 这里在应用就绪前用 {@code sys_sensitive_word} 的启用词整体覆盖，之后管理端每次改词都会再次热重载。
 * 词库为空时只告警不阻断启动——运营可能正准备重新录一套词，但必须让他在日志里看见「当前不拦任何词」。</p>
 */
@Component
public class SensitiveWordBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordBootstrap.class);

    private final SensitiveWordService sensitiveWordService;

    public SensitiveWordBootstrap(SensitiveWordService sensitiveWordService) {
        this.sensitiveWordService = sensitiveWordService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int size = sensitiveWordService.reloadFilter();
            if (size == 0) {
                log.warn("违禁词库为空：聊天与昵称暂不做敏感词拦截，请到「系统管理 → 违禁词库」配置");
            } else {
                log.info("违禁词库已从 sys_sensitive_word 加载 {} 条启用词（管理端改动即时生效）", size);
            }
        } catch (RuntimeException e) {
            // 表还没迁移 / DB 抖动：保留 classpath 词库继续跑，不阻断启动
            log.warn("违禁词库加载失败，继续使用 classpath 词库", e);
        }
    }
}
