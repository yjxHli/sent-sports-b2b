-- ============================================================================
-- V37：客服会话小结 + 客服经理角色 / 客服审查权限（2026-11-18）
-- 只新增，不修改历史迁移（sent_test 的 Flyway 校验和必须保持稳定）。
-- 表数：59 -> 60。
-- ============================================================================

-- 1) 会话小结：一个会话一条（客服填写完结情况，同会话重复提交走 upsert）
CREATE TABLE IF NOT EXISTS chat_session_note (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id  BIGINT UNSIGNED NOT NULL COMMENT '会话号',
    customer_id BIGINT UNSIGNED NOT NULL COMMENT '客户号',
    staff_id    BIGINT UNSIGNED NOT NULL COMMENT '填写小结的客服号',
    /** 问题概述：客服手动输入 */
    summary     VARCHAR(255) NOT NULL COMMENT '问题概述（客服手填）',
    /** QUALITY 商品品质问题 / INQUIRY 商品咨询 / UNCLEAR 未明确 */
    category    VARCHAR(24)  NOT NULL COMMENT '问题分类',
    /** CLEAR 明确信息 / NO_REPLY 未回复 */
    issue_info  VARCHAR(16)  NOT NULL COMMENT '用户问题信息',
    /** DONE 已完成 / UNRESOLVED 未解决 */
    result      VARCHAR(16)  NOT NULL COMMENT '处理结果',
    solution    VARCHAR(1024) NOT NULL COMMENT '解决办法',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_note_session (session_id),
    KEY idx_note_staff (staff_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服会话小结';

-- 2) 权限点：chat:note 客服填写；chat:audit 客服审查（精确匹配，不吃 '*'）
INSERT IGNORE INTO sys_permission (id, code, name, module, type) VALUES
    (15, 'chat:note',  '会话小结',   'CHAT', 'ACTION'),
    (16, 'chat:audit', '客服审查',   'CHAT', 'MENU');

-- 3) 客服经理角色（工号保留，登录名由 V38 回填到独立 username 列）
INSERT IGNORE INTO sys_role (id, code, name, data_scope, is_builtin)
VALUES (7, 'CHAT_MANAGER', '客服经理', 'ALL', 0);

-- 4) 客服经理：工作台 + 结束会话 + 小结 + 审查；客服：小结
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (7, 10), (7, 14), (7, 15), (7, 16), (2, 15);

-- 5) 精确匹配隔离：超管与客服经理显式持有 chat:audit；其余角色（含持 '*' 的业务岗）不授予
INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (1, 16);

-- 6) 客服经理演示账号：staff_no 保持工号风格，绝不与登录名混用
INSERT IGNORE INTO sys_staff
    (id, staff_no, name, phone, email, password_hash, role_id, leader_id, status, fail_count, token_version, deleted)
VALUES
    (9008, 'CHATMG001', '客服经理·周敏', '13800009008', 'chatmgr@sent.test', '123456', 7, 9001, 'ACTIVE', 0, 0, 0);
