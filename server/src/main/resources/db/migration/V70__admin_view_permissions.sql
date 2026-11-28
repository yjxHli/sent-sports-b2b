-- V70 批次 2 · RBAC 闭环：为审计报告 §2.1 列出的 22 个管理端只读接口登记细粒度 view 权限点。
-- 背景：批次 1 已把「/api/admin/** 无注解默认要求管理员」兜住匿名访问，
--       但任何员工角色（含仓管/财务）仍能读全部业务域；本迁移把「身份校验」升级为「权限点校验」。
-- 全部 INSERT IGNORE，重复执行不破坏既有授权；不新增表，仅补权限点与角色-权限关联。

INSERT IGNORE INTO sys_permission (code, name, module, type)
VALUES
    ('order:view',       '订单查看',       'ORDER',    'MENU'),
    ('finance:view',     '财务查看',       'FINANCE',  'MENU'),
    ('customer:view',    '客户查看',       'CUSTOMER', 'MENU'),
    ('chat:view',        '客服会话查看',   'CHAT',     'MENU'),
    ('custom:view',      '定制单查看',     'CUSTOM',   'MENU'),
    ('refund:view',      '退款单查看',     'REFUND',   'MENU'),
    ('product:view',     '商品查看',       'PRODUCT',  'MENU'),
    ('stock:view',       '库存查看',       'STOCK',    'MENU'),
    ('system:me:view',   '当前员工信息查看', 'SYSTEM', 'MENU'),
    -- 用户侧会话列表/当前会话也由「匿名可读」收紧为「登录可读」，一并登记以免 exact 校验漏掉。
    ('chat:read',        '客服会话读取',   'CHAT',     'ACTION');

-- 重新授权：演示口径下四个业务角色本来就持有 '*'（V14），这里再显式授予，
-- 确保将来回收通配符后管理端 22 个只读页面仍可正常使用，不会出现整页 403。
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.code IN (
    'order:view', 'finance:view', 'customer:view', 'chat:view', 'custom:view',
    'refund:view', 'product:view', 'stock:view', 'system:me:view', 'chat:read'
)
WHERE r.code IN (
    'SUPER_ADMIN', 'CUSTOMER_SERVICE', 'SALES_MANAGER', 'SALES', 'FINANCE', 'WAREHOUSE', 'CHAT_MANAGER'
);
