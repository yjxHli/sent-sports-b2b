-- V47 积分流水幂等键（P0 资金/权益修复）
-- 背景：订单完成发积分、退款扣回积分都要「同一来源只落一次」。
-- 与 growth_log 的 uk_growth_source 同构：唯一键落在 (客户, 来源类型, 来源单号)，
-- 由 INSERT IGNORE 承担幂等，不引入分布式锁、也不做「先查后写」的竞态判断。
-- 人工调整流水（后台改积分）不传来源时 source_type/source_id 为 NULL，
-- MySQL 唯一键允许多个 NULL，历史与后续的手工调整都不会被这条约束误伤。
ALTER TABLE point_log
    ADD UNIQUE KEY uk_point_source (customer_id, source_type, source_id);
