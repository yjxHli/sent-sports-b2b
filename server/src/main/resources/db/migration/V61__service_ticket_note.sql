-- ============================================================================
-- V61：会话小结升级为电商级「服务工单记录」（2026-11-25）
-- V57~V60 已占用，本迁移只能新增版本；只加列不改历史迁移。
-- 只加列，表数保持 67。
-- ============================================================================

ALTER TABLE chat_session_note
    ADD COLUMN sub_category    VARCHAR(64)   NULL COMMENT '子分类（问题分类的细分）' AFTER category,
    ADD COLUMN customer_demand VARCHAR(500)  NULL COMMENT '客户诉求' AFTER sub_category,
    ADD COLUMN verified_info   VARCHAR(500)  NULL COMMENT '核实到的订单/商品信息' AFTER customer_demand,
    ADD COLUMN need_followup   TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否需要回访：0 否 / 1 是' AFTER verified_info,
    ADD COLUMN followup_time   DATETIME(3)   NULL COMMENT '约定回访时间' AFTER need_followup,
    ADD COLUMN attachments_json TEXT          NULL COMMENT '附件凭证（fileId 列表 JSON）' AFTER followup_time;
