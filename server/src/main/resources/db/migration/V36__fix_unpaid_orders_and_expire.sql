-- ============================================================================
-- V36：补齐两处历史数据缺口
--
-- 1) 未付款订单不该有售后标记。历史上有 1 笔 WAIT_PAY/UNPAID 单被打了
--    after_sale_status = 'REFUNDED'，V33/V35 的「跳过售后单」条件因此把它排除，
--    金额（2250.60 元）停留在旧价。未付款订单不存在真实退款流程，标记是脏数据，
--    清掉后按新价重算即可，不影响任何退款流水（该单没有退款单）。
--
-- 2) 历史 WAIT_PAY 单的 expire_at 全是 NULL，OrderTimeoutJob 扫不到它们，
--    「超 5 分钟未支付自动取消」只对新单生效。这里按 created_at + 5 分钟补齐，
--    下一轮任务会把这些早已超时的单正常取消并释放预占库存。
-- ============================================================================

-- 1) 清掉未付款订单上的售后标记
UPDATE orders
SET after_sale_status = NULL
WHERE pay_status IN ('UNPAID', 'PAYING')
  AND after_sale_status IS NOT NULL;

-- 2) 未付款订单的金额按新零售价重算
UPDATE order_item oi
JOIN orders o ON o.id = oi.order_id
JOIN sku s ON s.id = oi.sku_id
SET oi.unit_price_cent = s.retail_price_cent,
    oi.amount_cent = s.retail_price_cent * oi.qty,
    oi.custom_fee_cent = CASE WHEN oi.custom_flag = 1 THEN 1000 * oi.qty ELSE 0 END
WHERE o.status NOT IN ('CANCELLED', 'CLOSED')
  AND o.pay_status IN ('UNPAID', 'PAYING');

UPDATE orders o
JOIN (
    SELECT order_id, SUM(amount_cent) AS goods, SUM(custom_fee_cent) AS custom_fee
    FROM order_item GROUP BY order_id
) t ON t.order_id = o.id
SET o.goods_amount_cent = t.goods,
    o.custom_fee_cent = t.custom_fee,
    o.payable_cent = GREATEST(0, t.goods + t.custom_fee + o.freight_cent - o.point_deduct_cent)
WHERE o.status NOT IN ('CANCELLED', 'CLOSED')
  AND o.pay_status IN ('UNPAID', 'PAYING');

UPDATE receivable r
JOIN orders o ON o.id = r.order_id
SET r.amount_cent = o.payable_cent
WHERE o.status NOT IN ('CANCELLED', 'CLOSED')
  AND o.pay_status IN ('UNPAID', 'PAYING');

-- 3) 历史待付款单补过期时间：下一轮 OrderTimeoutJob 会按业务代码取消并释放预占
UPDATE orders
SET expire_at = DATE_ADD(created_at, INTERVAL 5 MINUTE)
WHERE status = 'WAIT_PAY'
  AND expire_at IS NULL;
