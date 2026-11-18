-- V40 客服经理可换取危险操作确认令牌
-- 背景：客服审查的批量删除与发票管理同口径，必须先用 POST /api/admin/confirm-token
-- 换一次性令牌。该端点标 @RequirePerm("confirm:token:issue")，而客服经理（角色 7）
-- 没有 '*' 通配，缺此权限时删除按钮会静默失败（前端只提示「删除失败」）。
INSERT IGNORE INTO sys_permission (code, name, module, type)
VALUES ('confirm:token:issue', '危险操作确认令牌', 'ADMIN', 'ACTION');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT 7, id FROM sys_permission WHERE code = 'confirm:token:issue';
