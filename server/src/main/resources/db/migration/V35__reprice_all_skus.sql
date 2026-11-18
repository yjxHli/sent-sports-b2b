-- ============================================================================
-- V35：补齐「商品价格全部下调到 10~40 元」——V33 只覆盖了 9201~9209 九个演示 SKU，
-- 其余 111 个 SKU 仍是 55~995 元的旧价，管理端销售额/报表/应收与商城端订单金额不一致。
--
-- 映射规则：按原零售价升序排名，线性映射到 [1000, 4000] 分，
-- 既保证全部落进 10~40 元，又保留「贵的一直更贵」的相对关系。
-- 底价 = 零售 × 0.8，成本 = 零售 × 0.5（与 V33 的比例一致，cost < floor <= retail）。
--
-- 历史金额重算沿用 V33 的口径：跳过已取消、已关闭、已退款订单，
-- 退款流水/售后单是历史事实，不做改写。
-- ============================================================================

-- 1) 排名临时表（MySQL 不允许 UPDATE 目标表时直接子查询自身）
CREATE TEMPORARY TABLE tmp_sku_rank AS
SELECT id,
       ROW_NUMBER() OVER (ORDER BY retail_price_cent, id) AS rn,
       COUNT(*) OVER () AS cnt
FROM sku
WHERE retail_price_cent > 4000;

UPDATE sku s
JOIN tmp_sku_rank r ON r.id = s.id
SET s.retail_price_cent = ROUND(1000 + (r.rn - 1) * 3000 / GREATEST(r.cnt - 1, 1)),
    s.floor_price_cent  = ROUND((1000 + (r.rn - 1) * 3000 / GREATEST(r.cnt - 1, 1)) * 0.8),
    s.cost_price_cent   = ROUND((1000 + (r.rn - 1) * 3000 / GREATEST(r.cnt - 1, 1)) * 0.5);

DROP TEMPORARY TABLE tmp_sku_rank;

-- 2) 兜底：任何越界值拉回区间（幂等保护，重复执行不会继续漂移）
UPDATE sku SET retail_price_cent = LEAST(GREATEST(retail_price_cent, 1000), 4000);
UPDATE sku SET floor_price_cent = LEAST(GREATEST(floor_price_cent, 500), retail_price_cent);
UPDATE sku SET cost_price_cent = LEAST(GREATEST(cost_price_cent, 300), floor_price_cent);

-- 3) 阶梯价全量跟随新零售：1 件起按零售价，10 件起 95 折
UPDATE ladder_price lp
JOIN sku s ON s.id = lp.sku_id
SET lp.price_cent = CASE WHEN lp.min_qty = 1 THEN s.retail_price_cent
                         ELSE ROUND(s.retail_price_cent * 0.95) END;

-- 4) 历史订单行：按新零售价重算，定制行每行加收 10 元/件
UPDATE order_item oi
JOIN orders o ON o.id = oi.order_id
JOIN sku s ON s.id = oi.sku_id
SET oi.unit_price_cent = s.retail_price_cent,
    oi.amount_cent = s.retail_price_cent * oi.qty,
    oi.custom_fee_cent = CASE WHEN oi.custom_flag = 1 THEN 1000 * oi.qty ELSE 0 END
WHERE o.status NOT IN ('CANCELLED', 'CLOSED')
  AND o.pay_status NOT IN ('FULL_REFUNDED', 'PART_REFUNDED')
  AND (o.after_sale_status IS NULL OR o.after_sale_status NOT LIKE '%REFUND%');

-- 5) 订单头：商品金额 / 定制费 / 应付 / 已付 / 已收全量重算（应付不为负）
UPDATE orders o
JOIN (
    SELECT order_id, SUM(amount_cent) AS goods, SUM(custom_fee_cent) AS custom_fee
    FROM order_item GROUP BY order_id
) t ON t.order_id = o.id
SET o.goods_amount_cent = t.goods,
    o.custom_fee_cent = t.custom_fee,
    o.payable_cent = GREATEST(0, t.goods + t.custom_fee + o.freight_cent - o.point_deduct_cent),
    o.paid_cent = CASE WHEN o.pay_status = 'PAID'
                       THEN GREATEST(0, t.goods + t.custom_fee + o.freight_cent - o.point_deduct_cent)
                       ELSE o.paid_cent END,
    o.received_cent = CASE WHEN o.pay_status = 'PAID'
                           THEN GREATEST(0, t.goods + t.custom_fee + o.freight_cent - o.point_deduct_cent)
                           ELSE o.received_cent END
WHERE o.status NOT IN ('CANCELLED', 'CLOSED')
  AND o.pay_status NOT IN ('FULL_REFUNDED', 'PART_REFUNDED')
  AND (o.after_sale_status IS NULL OR o.after_sale_status NOT LIKE '%REFUND%');

-- 6) 应收台账跟随订单应付
UPDATE receivable r
JOIN orders o ON o.id = r.order_id
SET r.amount_cent = o.payable_cent
WHERE o.status NOT IN ('CANCELLED', 'CLOSED')
  AND o.pay_status NOT IN ('FULL_REFUNDED', 'PART_REFUNDED');

-- 7) 定制单费用快照：统一 10 元/件（此前多数为 0，少数是历史乱值）
UPDATE custom_order co
JOIN order_item oi ON oi.id = co.order_item_id
JOIN orders o ON o.id = oi.order_id
SET co.fee_cent = 1000 * co.qty
WHERE o.status NOT IN ('CANCELLED', 'CLOSED')
  AND o.pay_status NOT IN ('FULL_REFUNDED', 'PART_REFUNDED')
  AND (o.after_sale_status IS NULL OR o.after_sale_status NOT LIKE '%REFUND%');
