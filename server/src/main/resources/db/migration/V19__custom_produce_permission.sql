-- V19：补齐定制生产权限
-- 背景：定制件发货门禁要求定制单 FINISHED（已完工）——「审核通过 → 转生产 → 标记完工」是同一
--       条跟单链路上的三步，但 custom:produce 从未入库，客服/审核角色只能审图不能推进生产，
--       导致定制订单永远卡在「待发货」（发货接口 40901 状态冲突）。
INSERT IGNORE INTO sys_permission (id, code, name, module, type)
VALUES (2, 'custom:review', '定制审核', 'CUSTOM', 'ACTION');

INSERT IGNORE INTO sys_permission (id, code, name, module, type)
VALUES (20, 'custom:produce', '定制生产（转生产/完工）', 'CUSTOM', 'ACTION');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (2, 20);
