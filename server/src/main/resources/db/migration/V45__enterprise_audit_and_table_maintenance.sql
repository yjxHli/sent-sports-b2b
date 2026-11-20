-- V45 企业审核留痕 + 管理端表格软删标记（2026-11-19）
-- 只增列不删列：审核原因必须可见；删除一律只打标记，绝不动金额/流水本身。

ALTER TABLE enterprise_profile
    ADD COLUMN audit_remark VARCHAR(512) NULL COMMENT '审核意见/驳回原因' AFTER status,
    ADD COLUMN audited_at DATETIME(3) NULL COMMENT '审核时间' AFTER audit_remark,
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '管理端软删标记';

ALTER TABLE receivable
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '管理端软删标记';

ALTER TABLE payment_record
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '管理端软删标记';

ALTER TABLE write_off
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '管理端软删标记';

ALTER TABLE stock_flow
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '管理端软删标记';

ALTER TABLE sku_stock
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '管理端软删标记';

ALTER TABLE sys_operation_log
    ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '管理端软删标记';
