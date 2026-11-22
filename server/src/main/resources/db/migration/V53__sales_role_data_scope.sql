-- V53：报表口径改为「卖什么」后的数据范围收敛（2026-11-21）
--
-- 背景：业务报表从「按归属销售排行」改为「按 sku_id 的热销商品」，
-- 数据范围由 @DataScope 下推到 SQL；此前种子把销售主管 / 销售员都配成 ALL，
-- 等于主管能看全公司、销售员能看别人，与岗位职责不符。
--
-- 收敛口径（与 plan §7.9 的岗位约定一致）：
--   销售主管 SALES_MANAGER → TEAM（本人 + 直属下级）
--   销售员   SALES          → SELF（仅本人）
--   超管 / 财务 / 仓管 / 客服保持不变（ALL）
--
-- 只改角色配置，不动任何业务数据；带 WHERE 条件，重复执行安全。
UPDATE sys_role SET data_scope = 'TEAM' WHERE code = 'SALES_MANAGER' AND data_scope <> 'TEAM';
UPDATE sys_role SET data_scope = 'SELF' WHERE code = 'SALES' AND data_scope <> 'SELF';
