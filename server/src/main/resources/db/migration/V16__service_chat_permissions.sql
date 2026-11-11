-- V16：客服角色补齐客服工作台权限
-- 背景：V13 只给客服角色（role_id=2）授予 custom:review；而「客服工作台」前端路由要求 chat:serve、
--       接待动作后端要求 chat:assign。为使 service/123456 能完整跑通客服接待 + 定制审核，
--       此处补齐客服域权限并授予该角色（幂等 INSERT IGNORE）。
INSERT IGNORE INTO sys_permission (id, code, name, module, type) VALUES
    (10, 'chat:serve',    '客服工作台', 'CHAT', 'MENU'),
    (11, 'chat:assign',   '会话接待',   'CHAT', 'ACTION'),
    (12, 'chat:presence', '在线状态',   'CHAT', 'ACTION'),
    (13, 'chat:transfer', '会话转接',   'CHAT', 'ACTION'),
    (14, 'chat:close',    '结束会话',   'CHAT', 'ACTION');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES
    (2, 10), (2, 11), (2, 12), (2, 13), (2, 14);
