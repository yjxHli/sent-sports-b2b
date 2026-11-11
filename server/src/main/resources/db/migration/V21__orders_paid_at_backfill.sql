-- 批 4 销售趋势报表前置修复：orders.paid_at
--
-- 背景：V6 建表时已有 paid_at DATETIME(3) NULL，但生产支付回调链路
-- （PayCallbackUseCase -> OrderPaymentService.markPaid -> OrderRepositoryImpl.updateHeader）
-- 此前没有写入该列，导致真实支付订单的 paid_at 恒为 NULL。销售趋势报表严格以
-- paid_at 为时间基准，若不修复会把这些订单全部漏统计。
--
-- 1) 代码侧已同步修复：markPaid 现在透传回调的 paidAtMillis 落库到 paid_at，
--    updateHeader 用 COALESCE(#{paidAt}, paid_at) 只在有值时覆盖，不影响其它状态更新。
-- 2) 本迁移负责存量数据：对「已支付但 paid_at 仍是 NULL」的历史订单，
--    以 created_at 作为支付时间的最佳近似回填，保证历史销售业绩不丢失。
--    仅回填 PAID / PART_REFUNDED / FULL_REFUNDED，未支付订单保持 NULL。
-- 3) 加覆盖索引支撑近 N 天按 paid_at + pay_status 的聚合查询，避免全表扫描。

UPDATE orders
   SET paid_at = created_at
 WHERE paid_at IS NULL
   AND pay_status IN ('PAID', 'PART_REFUNDED', 'FULL_REFUNDED');

ALTER TABLE orders
    ADD INDEX idx_order_paid_at (pay_status, paid_at);
