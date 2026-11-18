-- 客户列表授信额度下调：V14 演示种子的 100000 / 50000 元过大。
-- 按用户口径压到 2000~4000 元区间（本表金额单位为分），不动账期与已用额度。
-- 只更新三家演示企业，后续通过认证生成的企业档案不受影响。
UPDATE enterprise_profile
SET credit_limit_cent = CASE customer_id
    WHEN 91001 THEN 350000
    WHEN 91002 THEN 280000
    WHEN 91003 THEN 320000
    ELSE credit_limit_cent
END
WHERE customer_id IN (91001, 91002, 91003);
