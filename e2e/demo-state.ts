/**
 * E2E 共用：给用例「现场补前提数据」。
 *
 * <p>背景（2026-11-26 定稿）：整套 E2E 共用一个演示库，种子只在灌库那一刻是对的。
 * 跑过几十轮之后，下面这些前提会自然消失：</p>
 *
 * <ul>
 *   <li>待发货单据被一批批发完 → 工作台「待发货」卡变 0；</li>
 *   <li>库存被买空 + safety_qty 归零 → 「预警 SKU」卡变 0、库存预警页空表；</li>
 *   <li>券被领到 claimed_qty 上限 → 领券中心按钮全变「已领完」；</li>
 *   <li>评价残留 → 详情页「0 条」、管理端「2 条」这类绝对数断言被打歪。</li>
 * </ul>
 *
 * <p>这里提供「只在缺失时才造」的兜底：数据好好的时候一行 SQL 都不写，
 * 缺了才临时造一条并返回 restore 回调，用例在 finally 里调用即可还原。
 * 这样用例既不依赖种子，也不会在健康的库上留下垃圾。</p>
 */
import { sql, sqlNumber } from './db';

/** 入库的口径必须与 DashboardReportMapper.countWarningSku() 完全一致 */
const WARNING_COUNT_SQL =
  'SELECT COUNT(*) FROM sku_stock '
  + 'WHERE GREATEST(physical_qty - locked_qty - pending_qty - frozen_qty, 0) '
  + '<= GREATEST(safety_qty, 0)';

const NOOP = (): void => {};

/**
 * 保证库里有 ≥1 个「可用量 <= 安全库存」的 SKU（工作台预警卡、库存预警页都读它）。
 *
 * <p>已有预警时什么都不做；没有时挑可用量最小的那个 SKU，把 safety_qty 抬到它的可用量
 * （于是 `可用 <= 安全` 成立），跑完还原原值——不动实物库存，不影响其它用例算量。
 * 挑「最小」而不是「最大」：造出来的预警行数字小，看起来像真的告急，也不会动辄上千。</p>
 */
export function ensureWarningSku(): () => void {
  if (sqlNumber(WARNING_COUNT_SQL) > 0) {
    return NOOP;
  }
  const row = sql(
    'SELECT sku_id, GREATEST(physical_qty - locked_qty - pending_qty - frozen_qty, 0) AS avail, '
    + 'safety_qty FROM sku_stock ORDER BY avail ASC, sku_id ASC LIMIT 1',
  );
  if (!row) {
    return NOOP;
  }
  const [skuId, avail, safety] = row.split(/\r?\n/)[0].split('\t');
  if (!skuId) {
    return NOOP;
  }
  sql(`UPDATE sku_stock SET safety_qty = ${Number(avail)} WHERE sku_id = ${Number(skuId)}`);
  return () => {
    sql(`UPDATE sku_stock SET safety_qty = ${Number(safety)} WHERE sku_id = ${Number(skuId)}`);
  };
}

/**
 * 保证库里存在「非预警」的 SKU（库存预警页要断言 `预警条数 < 全量条数`）。
 *
 * <p>只在极端情况（每个 SKU 都预警，通常是全库卖空且 safety_qty=0）才动手：
 * 给安全库存最小的那个 SKU 临时补实物库存让它脱离预警，跑完扣回去。</p>
 */
export function ensureHealthySku(): () => void {
  const total = sqlNumber('SELECT COUNT(*) FROM sku_stock');
  if (total === 0 || sqlNumber(WARNING_COUNT_SQL) < total) {
    return NOOP;
  }
  const row = sql(
    'SELECT sku_id, GREATEST(safety_qty, 0) AS safe, '
    + 'GREATEST(physical_qty - locked_qty - pending_qty - frozen_qty, 0) AS avail '
    + 'FROM sku_stock ORDER BY safe ASC, sku_id ASC LIMIT 1',
  );
  if (!row) {
    return NOOP;
  }
  const [skuId, safe, avail] = row.split(/\r?\n/)[0].split('\t');
  const need = Number(safe) + 1 - Number(avail);
  if (!skuId || !(need > 0)) {
    return NOOP;
  }
  sql(`UPDATE sku_stock SET physical_qty = physical_qty + ${need} WHERE sku_id = ${Number(skuId)}`);
  return () => {
    sql(`UPDATE sku_stock SET physical_qty = physical_qty - ${need} WHERE sku_id = ${Number(skuId)}`);
  };
}

/**
 * 保证有 ≥1 笔待发货单（工作台「待发货」卡 > 0）。
 *
 * <p>已有待发货就不动库；没有时补一笔 `status='WAIT_SHIP'` 的订单头 + 明细，
 * 跑完整单删除。刻意不写 `paid_at`/`paid_cent`，免得污染「今日成交」口径。</p>
 */
export function ensurePendingShipOrder(): () => void {
  const pending = sqlNumber(
    "SELECT COUNT(*) FROM orders WHERE status IN ('WAIT_SHIP', 'PART_SHIPPED')",
  );
  if (pending > 0) {
    return NOOP;
  }
  const skuId = sqlNumber('SELECT id FROM sku ORDER BY id ASC LIMIT 1');
  if (skuId <= 0) {
    return NOOP;
  }
  const orderNo = `ES${Date.now().toString().slice(-9)}`;
  sql(`INSERT INTO orders (order_no, customer_id, status, pay_status, goods_amount_cent, payable_cent, paid_cent)
VALUES ('${orderNo}', 9002, 'WAIT_SHIP', 'PAID', 100, 100, 0);
SET @oid = LAST_INSERT_ID();
INSERT INTO order_item (order_id, sku_id, qty, unit_price_cent, amount_cent)
VALUES (@oid, ${skuId}, 1, 100, 100);`);
  return () => {
    sql(`DELETE oi FROM order_item oi JOIN orders o ON o.id = oi.order_id WHERE o.order_no = '${orderNo}';
DELETE FROM orders WHERE order_no = '${orderNo}';`);
  };
}

/**
 * 领券中心用例的前提：演示客户名下「还有可领的券」。
 *
 * <p>两个漂移源：① `coupon_template.claimed_qty` 是累加计数，删了 `customer_coupon`
 * 也不会回落（`clean-test-data.sql` 就得专门归零它），累到 `total_qty` 后所有券变「已领完」；
 * ② 演示客户若历史领过某档券，那一档直接显示「已领取」。两者都会让「至少有一张可领」失效。</p>
 *
 * <p>只清 `source='CLAIM'` 的领取记录，运营发放的券一张不动；claimed_qty 归零与清库脚本同一手法。</p>
 */
export function ensureClaimableCoupons(customerId: number): void {
  sql('UPDATE coupon_template SET claimed_qty = 0 WHERE claimable = 1 AND deleted = 0');
  sql(`DELETE FROM customer_coupon WHERE customer_id = ${Number(customerId)} AND source = 'CLAIM'`);
}
