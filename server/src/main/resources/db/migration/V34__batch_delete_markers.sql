-- ============================================================================
-- V34：管理端六类列表批量删除（2026-11-17 用户要求）
-- 语义统一为「软删隐藏，不动已产生的财务/售后数据」：列表与 count 同条件过滤，
-- 历史订单、退款流水、应收台账继续可读，删除后可从库里恢复。
-- ============================================================================
ALTER TABLE refund_order   ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '管理端软删标记';
ALTER TABLE invoice_request ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '管理端软删标记';
ALTER TABLE contract        ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '管理端软删标记';
ALTER TABLE custom_order    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '管理端软删标记';
ALTER TABLE sku             ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '管理端软删标记（库存列表删除=下架并隐藏）';
ALTER TABLE orders          ADD COLUMN admin_deleted_at DATETIME(3) NULL COMMENT '管理端软删时间（客户侧 customer_deleted_at 不变）';
