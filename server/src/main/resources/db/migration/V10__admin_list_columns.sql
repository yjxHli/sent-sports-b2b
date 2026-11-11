-- 收口阶段：管理端「发票 / 合同 / 退款审核」真实列表所需列与索引。
-- V7 contract 表只有有效期/折扣/文件地址，前端 ContractVO 需要金额与状态，故补两列（存量行给安全默认值，待业务回填）。
ALTER TABLE contract
    ADD COLUMN amount_cent BIGINT NOT NULL DEFAULT 0 COMMENT '合同金额（分）' AFTER discount_rate,
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '合同状态 ACTIVE/EXPIRED/TERMINATED' AFTER file_url;

-- 列表按状态 + 时间 / 有效期筛选，补复合索引避免全表扫。
ALTER TABLE invoice_request
    ADD INDEX idx_invoice_status_created (status, created_at);
ALTER TABLE contract
    ADD INDEX idx_contract_status_expire (status, expire_date);
