package com.sent.sports.common.event;

/**
 * 领域事件发布接口（P3-02）。由基础设施层（Spring 实现，如 ApplicationEventPublisher 适配）注入。
 * 用例只依赖此接口，不直接耦合 Spring。
 */
public interface DomainEventPublisher {
    void publish(Object event);
}
