package com.sent.sports.customize.repository.impl;

import com.sent.sports.customize.domain.CustomDraft;
import com.sent.sports.customize.domain.CustomOrder;
import com.sent.sports.customize.domain.CustomReviewLog;
import com.sent.sports.customize.domain.CustomStatus;
import com.sent.sports.customize.repository.CustomizeRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 定制单仓储（进程内适配器，Phase 10 起不再注册为 Bean）。
 * <p>真库实现见 {@link DbCustomizeRepository}：{@code custom_order} / {@code custom_draft} /
 * {@code custom_review_log} 全部落 MySQL。本类保留作纯内存语义对照实现，供单元测试按需
 * 手工 new 出来自测，禁止再参与 Spring 装配（避免双实现导致 Bean 冲突）。</p>
 */
public class InMemoryCustomizeRepository implements CustomizeRepository {

    private final Map<String, CustomOrder> byId = new ConcurrentHashMap<>();
    private final List<CustomDraft> drafts = new CopyOnWriteArrayList<>();
    private final List<CustomReviewLog> reviewLogs = new CopyOnWriteArrayList<>();

    @Override
    public Optional<CustomStatus> statusOf(String orderNo, long skuId) {
        return Optional.empty();
    }

    @Override
    public Optional<CustomOrder> findByCustomOrderId(String customOrderId) {
        return Optional.ofNullable(byId.get(customOrderId));
    }

    @Override
    public void save(CustomOrder customOrder) {
        byId.put(customOrder.getCustomOrderId(), customOrder);
    }

    @Override
    public void insertDraft(CustomDraft draft) {
        drafts.add(draft);
    }

    @Override
    public void insertReviewLog(CustomReviewLog log) {
        reviewLogs.add(log);
    }
}
