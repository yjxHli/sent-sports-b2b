package com.sent.sports.customize.repository;

import com.sent.sports.customize.domain.CustomDraft;
import com.sent.sports.customize.domain.CustomOrder;
import com.sent.sports.customize.domain.CustomReviewLog;
import com.sent.sports.customize.domain.CustomStatus;

import java.util.Optional;

/**
 * 定制仓储端口（P3-15 引入，实现见 Phase 5）。
 * 既支持按 订单号 + SKU 查询状态的轻量视图（供发货/退款门面判定），也支持按定制单号加载聚合与写图稿版本。
 */
public interface CustomizeRepository {
    /** 该订单行对应定制单状态；无定制记录返回 empty（视为非定制，可发）。 */
    Optional<CustomStatus> statusOf(String orderNo, long skuId);

    /** 按定制单号加载聚合（含状态/版本/驳回次数/审核截止）。 */
    Optional<CustomOrder> findByCustomOrderId(String customOrderId);

    /** 保存（Upsert）定制单聚合。 */
    void save(CustomOrder customOrder);

    /** 写入一条图稿版本（只 insert，不 update）。 */
    void insertDraft(CustomDraft draft);

    /** 写入一条审图日志（只 insert，不 update；重复审核由 DB 唯一键吞掉）。 */
    void insertReviewLog(CustomReviewLog log);
}
