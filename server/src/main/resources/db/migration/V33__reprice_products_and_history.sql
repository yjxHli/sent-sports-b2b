-- ============================================================================
-- V33：商品价格整体下调到 10~40 元区间 + 定制件加收 10 元/件 + 历史金额重算
-- 2026-11-17 用户要求：原零售价 55~459 元明显偏高，管理端销售额/报表/应收
-- 与商城端订单金额都要按新价口径整改。
--
-- 只改「未取消、未退款」的订单：已退款/已关闭订单保留原始金额与退款流水，
-- 避免出现退款额大于订单额的脏数据。
-- ============================================================================

-- 1) 9 个 SKU：零售 / 底价 / 成本同时压到区间内，保证 cost < floor <= retail
UPDATE sku SET
    retail_price_cent = CASE id
        WHEN 9201 THEN 3990 WHEN 9202 THEN 3800 WHEN 9203 THEN 1990
        WHEN 9204 THEN 2000 WHEN 9205 THEN 2990 WHEN 9206 THEN 3200
        WHEN 9207 THEN 3500 WHEN 9208 THEN 1200 WHEN 9209 THEN 1000 END,
    floor_price_cent = CASE id
        WHEN 9201 THEN 3200 WHEN 9202 THEN 3100 WHEN 9203 THEN 1600
        WHEN 9204 THEN 1600 WHEN 9205 THEN 2400 WHEN 9206 THEN 2600
        WHEN 9207 THEN 2800 WHEN 9208 THEN 1000 WHEN 9209 THEN 850 END,
    cost_price_cent = CASE id
        WHEN 9201 THEN 2500 WHEN 9202 THEN 2400 WHEN 9203 THEN 1200
        WHEN 9204 THEN 1200 WHEN 9205 THEN 1800 WHEN 9206 THEN 2000
        WHEN 9207 THEN 2200 WHEN 9208 THEN 800  WHEN 9209 THEN 650 END
WHERE id IN (9201, 9202, 9203, 9204, 9205, 9206, 9207, 9208, 9209);

-- 2) 阶梯价跟随新零售：1~9 件按零售价，10 件起 95 折
UPDATE ladder_price lp JOIN sku s ON s.id = lp.sku_id
SET lp.price_cent = CASE WHEN lp.min_qty = 1 THEN s.retail_price_cent
                         ELSE ROUND(s.retail_price_cent * 0.95) END
WHERE lp.sku_id IN (9201, 9202, 9203, 9204, 9205, 9206, 9207, 9208, 9209);

-- 3) 历史订单行：按新零售价重算，定制行每行加收 10 元/件
UPDATE order_item oi
JOIN orders o ON o.id = oi.order_id
JOIN sku s ON s.id = oi.sku_id
SET oi.unit_price_cent = s.retail_price_cent,
    oi.amount_cent = s.retail_price_cent * oi.qty,
    oi.custom_fee_cent = CASE WHEN oi.custom_flag = 1 THEN 1000 * oi.qty ELSE 0 END
WHERE o.status NOT IN ('CANCELLED', 'CLOSED')
  AND o.pay_status NOT IN ('FULL_REFUNDED', 'PART_REFUNDED')
  AND (o.after_sale_status IS NULL OR o.after_sale_status NOT LIKE '%REFUND%');

-- 4) 订单头：商品金额 / 定制费 / 应付 / 已付全量重算。
--    报表统一按 orders.paid_cent + paid_at 汇总，这里改完销售额与趋势自动跟随。
UPDATE orders o
JOIN (
    SELECT order_id, SUM(amount_cent) AS goods, SUM(custom_fee_cent) AS custom_fee
    FROM order_item GROUP BY order_id
) t ON t.order_id = o.id
SET o.goods_amount_cent = t.goods,
    o.custom_fee_cent = t.custom_fee,
    o.payable_cent = t.goods + t.custom_fee + o.freight_cent - o.point_deduct_cent,
    o.paid_cent = CASE WHEN o.pay_status = 'PAID'
                       THEN t.goods + t.custom_fee + o.freight_cent - o.point_deduct_cent
                       ELSE o.paid_cent END,
    o.received_cent = CASE WHEN o.pay_status = 'PAID'
                           THEN t.goods + t.custom_fee + o.freight_cent - o.point_deduct_cent
                           ELSE o.received_cent END
WHERE o.status NOT IN ('CANCELLED', 'CLOSED')
  AND o.pay_status NOT IN ('FULL_REFUNDED', 'PART_REFUNDED')
  AND (o.after_sale_status IS NULL OR o.after_sale_status NOT LIKE '%REFUND%');

-- 5) 应收台账跟随订单应付（回款/核销流水是历史事实，不动）
UPDATE receivable r JOIN orders o ON o.id = r.order_id
SET r.amount_cent = o.payable_cent
WHERE o.status NOT IN ('CANCELLED', 'CLOSED')
  AND o.pay_status NOT IN ('FULL_REFUNDED', 'PART_REFUNDED');
