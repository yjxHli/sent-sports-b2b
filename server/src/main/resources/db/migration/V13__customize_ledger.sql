-- V13 定制图稿 / 审图真库落账 + 客服种子账号
-- 只做列/索引/种子变更，不新增业务表，表数仍为 54。

-- 1) 图稿文件 ID：前端提交的是字符串（f_<ts> / 文件服务 ID），原 BIGINT 无法承载
ALTER TABLE custom_draft
    MODIFY COLUMN file_id VARCHAR(64) NULL COMMENT '文件服务 ID（字符串）';

-- 2) 领域聚合有 lockedVersion（审核通过时锁定的图稿版本），表缺列
ALTER TABLE custom_order
    ADD COLUMN locked_version INT NOT NULL DEFAULT 0 AFTER reject_count;

-- 3) 审图日志按「定制单 + 图稿版本 + 审核结果」唯一，重复审核由 DB 唯一键兜底，
--    与「流水必须记，防重只用 DB 唯一索引」口径一致
ALTER TABLE custom_review_log
    ADD UNIQUE KEY uk_review_once (custom_order_id, draft_version, result);

-- 4) 验收客服账号：service / 123456（userId 9003），仅授予 custom:review
INSERT IGNORE INTO sys_role (id, code, name, data_scope, is_builtin)
VALUES (2, 'CUSTOMER_SERVICE', '客服', 'ALL', 0);
INSERT IGNORE INTO sys_permission (id, code, name, module, type)
VALUES (2, 'custom:review', '定制审核', 'CUSTOM', 'ACTION');
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 2);
INSERT IGNORE INTO sys_staff
    (id, staff_no, name, phone, email, password_hash, role_id, status, token_version, deleted)
VALUES
    (9003, 'SERVICE001', '验收客服', '13800009003', 'service@sent.test', '123456', 2, 'ACTIVE', 0, 0);

-- 5) 验收商品开放定制（UV 定制件走 SKU 9102）
UPDATE product SET can_customize = 1 WHERE id = 1001;
