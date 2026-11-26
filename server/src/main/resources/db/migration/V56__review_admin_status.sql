-- V56 商品评价管理（运营侧）
--
-- 背景：V55 的评价只有「写 / 读」两条路径，违规内容一旦提交就没有下架手段。
-- 本迁移只做三件小事，不动既有提交逻辑：
--   1) product_review 增 status 列（NORMAL 正常 / HIDDEN 已隐藏），默认 NORMAL，
--      历史数据自动落到「正常」，用户端查询侧据此过滤；
--   2) 新增权限点 system:review:manage（MENU 型，控制菜单可见 + 接口准入）；
--   3) 新增「运营」角色并授予该权限点；权限点以 exact 方式校验，
--      演示库里持 '*' 的业务角色（销售/财务/仓管等）不会顺带拿到评价管理。
--
-- 口径：隐藏只是「用户端不展示」，不影响「同一订单同一商品只能评一次」的防重约束——
-- 隐藏后再评仍会被 uk_review_order_product 拦住，运营可随时恢复原评价。

ALTER TABLE product_review
    ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'NORMAL'
        COMMENT 'NORMAL=正常展示 / HIDDEN=已隐藏（运营下架违规内容）' AFTER is_anonymous,
    ADD KEY idx_review_status (status, id);

INSERT IGNORE INTO sys_permission (code, name, module, type)
VALUES ('system:review:manage', '商品评价管理', 'SYSTEM', 'MENU');

-- 运营角色：只承载评价治理，不建演示账号（避免打乱演示员工列表口径）
INSERT IGNORE INTO sys_role (id, code, name, data_scope, is_builtin)
VALUES (8, 'OPERATIONS', '运营', 'ALL', 0);

-- 超管显式持有：exact 校验不认 '*'，所以必须补这一行，否则超管自己也进不去
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 1, id FROM sys_permission WHERE code = 'system:review:manage';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 8, id FROM sys_permission WHERE code = 'system:review:manage';
