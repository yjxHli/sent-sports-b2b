-- V12 成长值流水幂等键（验收缺口补齐）
-- growth_log 原本只有二级索引，无法用 DB 唯一键防止「同一订单重复发放成长值」。
-- 按项目口径「流水必须记，防重只用 DB 唯一索引，不引入分布式锁」，
-- 这里补 (customer_id, source_type, source_id) 唯一键：
-- 同一客户 + 同一来源类型 + 同一来源单号至多一条流水，INSERT IGNORE 返回 0 即视为重放。
-- 只加索引不加表，业务表数仍为 54。
ALTER TABLE growth_log
    ADD UNIQUE KEY uk_growth_source (customer_id, source_type, source_id);
