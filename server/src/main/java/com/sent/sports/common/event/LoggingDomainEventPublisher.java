package com.sent.sports.common.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 领域事件发布（Phase 5 进程内实现）：先落日志，保证用例链路可跑通。
 * <p>后续如需事务后异步监听，替换为 Spring {@code ApplicationEventPublisher} 适配器即可，用例层零改动。
 */
@Component
public class LoggingDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingDomainEventPublisher.class);

    @Override
    public void publish(Object event) {
        log.info("[DOMAIN-EVENT] {}", event == null ? "null" : event.getClass().getSimpleName());
    }
}
