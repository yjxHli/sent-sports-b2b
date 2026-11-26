-- V60 智能客服话术配置（2026-11-24）
--
-- 背景：V59 的知识库靠 SQL 种子维护，运营改一句话要发版。本迁移只做三件事：
--   1) faq_knowledge 增 status（ENABLED / DISABLED）：停用只是「暂时不答」，条目保留，
--      不要用物理删除——命中次数是运营评估话术有效性的唯一依据，删了就没了；
--   2) 新增权限点 chat:faq:manage（MENU 型，控菜单 + 接口准入）；
--   3) 授权给超管与客服经理两个角色，其余业务角色（持 '*'）不授。
--
-- 口径：权限点以 exact 方式校验。演示库里客服（CUSTOMER_SERVICE）、销售、财务都持 '*'，
-- 不精确匹配的话「只有主管和超管能改话术」形同虚设——话术配置一旦被乱改，
-- 所有客户看到的自动回复都会变，属于内容事故而不是权限小瑕疵。

ALTER TABLE faq_knowledge
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'ENABLED'
        COMMENT 'ENABLED=启用（参与匹配）/ DISABLED=停用（保留条目但不回复）' AFTER hit_count,
    ADD KEY idx_faq_status (status, id);

-- 默认排序按「更新时间新在上」，列表第一页就是刚改过的话术，运营不用翻页找
ALTER TABLE faq_knowledge
    ADD KEY idx_faq_updated (updated_at);

INSERT IGNORE INTO sys_permission (code, name, module, type)
VALUES ('chat:faq:manage', '智能客服话术配置', 'CHAT', 'MENU');

-- 超管显式持有：exact 校验不认 '*'，没有这一行超管自己也进不去
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE code = 'chat:faq:manage';

-- 客服经理（CHAT_MANAGER，id=7）是话术的业务负责人
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 7, id FROM sys_permission WHERE code = 'chat:faq:manage';
