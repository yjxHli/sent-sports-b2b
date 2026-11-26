-- ============================================================================
-- V63：客户标签（2026-11-25）
--
-- 背景：同一批客户反复进线索券/差评要挟，客服之间只能靠群聊口头提醒，
--       换个客服接班就完全不知道对面是谁。本迁移建两张表 + 一套初始标签目录。
--
-- 设计取舍：
--   1) 标签定义（customer_tag）与「谁被打了什么标」（customer_tag_link）分开：
--      标签是后台维护的字典，客服只能选不能造——一人一套说法标签就失去检索意义；
--   2) 关联表「取消标签」用物理删除，历史由 sys_operation_log 承担。
--      软删列会让每个查询都多一个过滤条件，漏一处就出现「取消了还在」；
--   3) 防重靠 uk_customer_tag，配合 INSERT IGNORE——重复提交影响 0 行也算成功，
--      与项目「防重只用 DB 唯一索引」的口径一致，不引入分布式锁；
--   4) 权限点 customer:tag:manage 以 exact 校验：演示库客服/销售/财务都持 '*'，
--      不精确匹配的话「只有后台能决定有哪些标签」形同虚设。
--
-- 表数：67 -> 69。种子 24 条（四大类各 6 条），运营可在管理端自行增删改。
-- ============================================================================

CREATE TABLE IF NOT EXISTS customer_tag (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(32)     NOT NULL COMMENT '标签名（全表唯一，含已停用）',
    category    VARCHAR(16)     NOT NULL
                COMMENT 'EXCELLENT 优质/正面 / NEUTRAL 中性普通 / RISK 风险留意 / MALICIOUS 恶意',
    status      VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE'
                COMMENT 'ACTIVE=弹窗可选 / DISABLED=不再可选，已打上的仍展示',
    sort_no     INT             NOT NULL DEFAULT 0 COMMENT '同分类内的展示顺序',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT          NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (name),
    KEY idx_tag_category (category, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户标签定义';

CREATE TABLE IF NOT EXISTS customer_tag_link (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    customer_id BIGINT UNSIGNED NOT NULL COMMENT '客户号',
    tag_id      BIGINT UNSIGNED NOT NULL COMMENT '标签号',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by  BIGINT          NULL COMMENT '打标的客服号（留痕主记录在 sys_operation_log）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_customer_tag (customer_id, tag_id),
    KEY idx_link_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户 ↔ 标签关联';

INSERT IGNORE INTO sys_permission (code, name, module, type)
VALUES ('customer:tag:manage', '客户标签管理', 'CUSTOMER', 'MENU');

-- 超管显式持有：exact 校验不认 '*'，没有这一行超管自己也进不去维护页
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE code = 'customer:tag:manage';

-- 客服经理（CHAT_MANAGER，id=7）是标签口径的业务负责人：标签写什么、怎么分类由她定
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 7, id FROM sys_permission WHERE code = 'customer:tag:manage';

-- 初始目录：与客服团队现有口径一一对应，sort_no 在分类内递增（新增标签接在最后，不插队）
INSERT IGNORE INTO customer_tag (name, category, status, sort_no) VALUES
    ('忠实老客',     'EXCELLENT', 'ACTIVE', 1),
    ('复购常客',     'EXCELLENT', 'ACTIVE', 2),
    ('沟通友善',     'EXCELLENT', 'ACTIVE', 3),
    ('理性买家',     'EXCELLENT', 'ACTIVE', 4),
    ('好评用户',     'EXCELLENT', 'ACTIVE', 5),
    ('配合度高',     'EXCELLENT', 'ACTIVE', 6),

    ('新客',         'NEUTRAL',   'ACTIVE', 1),
    ('价格敏感',     'NEUTRAL',   'ACTIVE', 2),
    ('轻度索券',     'NEUTRAL',   'ACTIVE', 3),
    ('犹豫观望',     'NEUTRAL',   'ACTIVE', 4),
    ('普通消费者',   'NEUTRAL',   'ACTIVE', 5),
    ('沟通正常',     'NEUTRAL',   'ACTIVE', 6),

    ('历史差评多',   'RISK',      'ACTIVE', 1),
    ('投诉倾向',     'RISK',      'ACTIVE', 2),
    ('高频退换',     'RISK',      'ACTIVE', 3),
    ('情绪急躁',     'RISK',      'ACTIVE', 4),
    ('爱索要补偿',   'RISK',      'ACTIVE', 5),
    ('需要重点沟通', 'RISK',      'ACTIVE', 6),

    ('羊毛党',       'MALICIOUS', 'ACTIVE', 1),
    ('差评勒索',     'MALICIOUS', 'ACTIVE', 2),
    ('恶意退款',     'MALICIOUS', 'ACTIVE', 3),
    ('职业打假',     'MALICIOUS', 'ACTIVE', 4),
    ('辱骂用户',     'MALICIOUS', 'ACTIVE', 5),
    ('黑名单用户',   'MALICIOUS', 'ACTIVE', 6);
