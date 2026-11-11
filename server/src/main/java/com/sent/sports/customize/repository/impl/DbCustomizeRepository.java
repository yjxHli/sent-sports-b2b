package com.sent.sports.customize.repository.impl;

import com.sent.sports.common.error.BizException;
import com.sent.sports.common.error.ErrorCode;
import com.sent.sports.customize.domain.CustomDraft;
import com.sent.sports.customize.domain.CustomOrder;
import com.sent.sports.customize.domain.CustomReviewLog;
import com.sent.sports.customize.domain.CustomStatus;
import com.sent.sports.customize.mapper.CustomOrderMapper;
import com.sent.sports.customize.mapper.po.CustomOrderPO;
import com.sent.sports.customize.repository.CustomizeRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * 定制仓储真库实现（Phase 10）：把原先进程内 Map 的定制聚合状态、图稿版本、审图日志
 * 全部落到 {@code custom_order} / {@code custom_draft} / {@code custom_review_log}。
 *
 * <p>防重口径与项目一致：图稿版本由 {@code uk_draft_version(custom_order_id, version)} 守护，
 * 审图日志由 V13 的 {@code uk_review_once(custom_order_id, draft_version, result)} 守护
 * （INSERT IGNORE 返回 0 即视为重放），主记录由 {@code uk_custom_no} 守护；全程不引入分布式锁。</p>
 *
 * <p>只依赖本模块 Mapper / 领域，符合 ArchUnit 规则 4。</p>
 */
@Repository
public class DbCustomizeRepository implements CustomizeRepository {

    private final CustomOrderMapper customOrderMapper;

    public DbCustomizeRepository(CustomOrderMapper customOrderMapper) {
        this.customOrderMapper = customOrderMapper;
    }

    @Override
    public Optional<CustomStatus> statusOf(String orderNo, long skuId) {
        String status = customOrderMapper.selectStatusOfOrderLine(orderNo, skuId);
        if (status == null || status.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(CustomStatus.valueOf(status));
    }

    @Override
    public Optional<CustomOrder> findByCustomOrderId(String customOrderId) {
        CustomOrderPO po = customOrderMapper.selectByCustomNo(customOrderId);
        if (po == null) {
            return Optional.empty();
        }
        CustomOrder order = new CustomOrder(
                po.getCustomNo(),
                CustomStatus.valueOf(po.getStatus()),
                po.getCurrentVersion() == null ? 0 : po.getCurrentVersion(),
                po.getRejectCount() == null ? 0 : po.getRejectCount(),
                toMillis(po.getReviewDeadline()));
        order.setLockedVersion(po.getLockedVersion() == null ? 0 : po.getLockedVersion());
        if (po.getProductionStartAt() != null) {
            order.setStartedAtMillis(toMillis(po.getProductionStartAt()));
        }
        if (po.getFinishedAt() != null) {
            order.setFinishedAtMillis(toMillis(po.getFinishedAt()));
        }
        return Optional.of(order);
    }

    @Override
    public void save(CustomOrder customOrder) {
        CustomOrderMapper.CustomOrderState state = new CustomOrderMapper.CustomOrderState();
        state.setCustomNo(customOrder.getCustomOrderId());
        state.setStatus(customOrder.getStatus().name());
        state.setCurrentVersion(customOrder.getCurrentVersion());
        state.setRejectCount(customOrder.getRejectCount());
        state.setLockedVersion(customOrder.getLockedVersion());
        state.setReviewDeadline(toDateTime(customOrder.getReviewDeadlineMillis()));
        state.setProductionStartAt(toDateTime(customOrder.getStartedAtMillis()));
        state.setFinishedAt(toDateTime(customOrder.getFinishedAtMillis()));

        // 先判存在再决定 insert / update，避免依赖 MySQL「受影响行数」的驱动差异
        if (customOrderMapper.selectIdByCustomNo(customOrder.getCustomOrderId()) == null) {
            customOrderMapper.insertAggregateIfAbsent(state);
        } else {
            customOrderMapper.updateAggregate(state);
        }
    }

    @Override
    public void insertDraft(CustomDraft draft) {
        long customOrderPk = requirePk(draft.getCustomOrderId());
        customOrderMapper.insertDraft(customOrderPk, draft.getVersion(), draft.getFileId(),
                draft.getFileName(), draft.getFileSize(), toDateTime(draft.getUploadedAtMillis()));
    }

    @Override
    public void insertReviewLog(CustomReviewLog log) {
        long customOrderPk = requirePk(log.getCustomOrderId());
        customOrderMapper.insertReviewLog(customOrderPk, log.getDraftVersion(), log.getResult(),
                log.getReasonCode(), log.getRemark(), log.getReviewerId(),
                toDateTime(log.getCreatedAtMillis()));
    }

    private long requirePk(String customNo) {
        Long pk = customOrderMapper.selectIdByCustomNo(customNo);
        if (pk == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "定制单不存在：" + customNo);
        }
        return pk;
    }

    private static LocalDateTime toDateTime(long millis) {
        return millis <= 0L ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
    }

    private static long toMillis(LocalDateTime value) {
        return value == null ? 0L : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
