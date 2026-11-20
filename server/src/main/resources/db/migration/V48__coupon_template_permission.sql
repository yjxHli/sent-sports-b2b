-- V48 券模板管理拆出独立权限点（R15）：
-- 背景：券模板的「分页 / 新建 / 批量删除」原本复用 customer:grant（客户授信），
--       语义错配——拥有「客户授信」权限的人可以自己造券并全量发券，等于绕过券档位审核。
-- 处理：拆出 coupon:template:manage，建券与发券可按岗分授；
--       发券（含发券下拉、发放记录、批量删除发放记录）继续用 customer:grant 不变。
-- 说明：超管与演示角色 3~6 持有 '*' 通配，天然可用；客服经理（角色 7）只保留发券权，
--       需要建券时由管理员在「角色权限矩阵」里显式授予本权限点。
INSERT IGNORE INTO sys_permission (code, name, module, type)
VALUES ('coupon:template:manage', '优惠券模板管理', 'COUPON', 'ACTION');
