/**
 * 待付款订单超 5 分钟自动取消（2026-11-17 用户要求，TDD）。
 *
 * 背景：OrderTimeoutJob 早就存在，但它扫的是 orders.expire_at，而下单一路没写这一列——
 * 历史单与新建单的 expire_at 全是 NULL，任务每分钟空转、取消 0 单。
 * 本用例验证整条链路：下单写入 expire_at（= 创建时间 + 5 分钟）→ 回拨到 6 分钟前
 * → 定时任务取消订单 → 库存预占被释放。
 *
 * 断言口径：
 *   1) 下单落库的 expire_at 非空且恰好比 created_at 晚 5 分钟；
 *   2) 一轮调度内 WAIT_PAY → CANCELLED；
 *   3) sku_stock.locked_qty 回落到下单前（真释放，不是只改状态）。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type APIRequestContext } from '@playwright/test';
import { pickSellableSku } from './pick-product';

const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');
const USER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const USER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const CUSTOMER_ID = Number(process.env.E2E_CUSTOMER_ID ?? 9002);
const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';
/** 定时任务每分钟一轮，留足两轮余量。 */
const POLL_TIMEOUT_MS = 150_000;

function mysqlBin(): string {
  if (process.env.E2E_MYSQL_BIN) return process.env.E2E_MYSQL_BIN;
  return existsSync(LOCAL_MYSQL) ? LOCAL_MYSQL : 'mysql';
}

function sql(statement: string, tabular = false): string {
  const args = ['-h127.0.0.1', '-P3306', '-uroot', `-p${process.env.E2E_DB_PASSWORD ?? '123456'}`,
    'sent_dev', '-N', '-B'];
  if (tabular) args.push('-e', statement);
  else args.push('-e', statement);
  return execFileSync(mysqlBin(), args, { encoding: 'utf8' }).trim();
}

async function api(request: APIRequestContext, method: 'GET' | 'POST', path: string,
                   data?: unknown, token?: string): Promise<any> {
  // 2026-11-28 批次 2：POST /api/orders 已挂 @Idempotent，缺 Idempotency-Key 会 40002。
  // 统一给写请求带一个每次唯一的键（其它 POST 没挂注解，多带一个头无副作用）。
  const res = await request.fetch(`${API_URL}${path}`, {
    method,
    data,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(method === 'POST' ? { 'Idempotency-Key': `E2E-${Date.now()}-${Math.random().toString(36).slice(2, 10)}` } : {}),
    },
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

test('待付款超 5 分钟：订单自动取消并释放库存', async ({ request }) => {
  test.setTimeout(240_000);

  const login = await api(request, 'POST', '/auth/login', {
    username: USER_PHONE, password: USER_PASSWORD, aud: 'user',
    deviceId: `e2e-order-timeout-${Date.now()}`,
  });
  const userToken = login?.data?.accessToken as string;
  expect(userToken, `用户登录失败：${JSON.stringify(login)}`).toBeTruthy();

  // 收货地址（种子数据里 9002 必有一条默认地址）
  const addresses = await api(request, 'GET', '/addresses', undefined, userToken);
  const addressId = Number(addresses?.data?.[0]?.id ?? addresses?.data?.[0]?.addressId);
  expect(addressId, `没有可用收货地址：${JSON.stringify(addresses)}`).toBeGreaterThan(0);

  // 现场挑有货规格，坚持不写死 SKU
  const picked = await pickSellableSku(request, 1, userToken);
  const skuId = Number(picked.skuId);

  // 清场：同一 SKU 上若还压着历史待付款单，超时任务会在本用例等待期间顺带取消它们、
  // 释放预占，把 locked_qty 搅成 -1，断言「取消后回到 lockedBefore」就会假红。
  // 这里先用业务取消（不是裸改 sku_stock）把这些单清掉，保证只有本次这一笔在锁。
  const staleNos = sql(
    'SELECT o.order_no FROM orders o JOIN order_item oi ON oi.order_id = o.id '
    + `WHERE oi.sku_id = ${skuId} AND o.customer_id = ${CUSTOMER_ID} AND o.status = 'WAIT_PAY' `
    + 'ORDER BY o.id DESC LIMIT 20;',
  ).split('\n').map((s) => s.trim()).filter(Boolean);
  for (const staleNo of staleNos) {
    await api(request, 'POST', `/orders/${staleNo}/cancel`, undefined, userToken);
  }

  const lockedBefore = Number(sql(`SELECT locked_qty FROM sku_stock WHERE sku_id = ${skuId};`));

  const idemKey = `E2E-TIMEOUT-${Date.now()}`;
  const created = await api(request, 'POST', '/orders', {
    customerId: CUSTOMER_ID,
    addressId,
    idemKey,
    availablePoints: 0,
    freightFreeThresholdCent: 19900,
    freightFeeCent: 1200,
    items: [{
      skuId, qty: 1, customerTypeCode: 'PERSON', memberDiscountRate: 100,
      agreementDiscountRate: 100, allowStacking: false, ladderTiers: [], customFlag: false,
    }],
  }, userToken);
  const orderNo = created?.data?.orderNo as string;
  expect(orderNo, `下单失败：${JSON.stringify(created)}`).toBeTruthy();
  expect(created?.data?.status).toBe('WAIT_PAY');

  try {
    // 1) 下单瞬间写入 5 分钟过期时间
    const stamp = sql(
      `SELECT TIMESTAMPDIFF(SECOND, created_at, expire_at) FROM orders WHERE order_no = '${orderNo}';`,
    );
    expect(Number(stamp), 'expire_at 必须为 NULL 之外的真实时间，超时任务才扫得到')
      .toBeGreaterThanOrEqual(299);
    expect(Number(stamp)).toBeLessThanOrEqual(301);

    // 下单应先锁住库存，否则第 3 步的「释放」无从验证
    const lockedAfterOrder = Number(sql(`SELECT locked_qty FROM sku_stock WHERE sku_id = ${skuId};`));
    expect(lockedAfterOrder).toBe(lockedBefore + 1);

    // 2) 回拨到 6 分钟前，等价于已超时 1 分钟
    sql(`UPDATE orders SET expire_at = DATE_SUB(NOW(3), INTERVAL 6 MINUTE) WHERE order_no = '${orderNo}';`);

    // 3) 等一轮定时任务把订单收掉
    const deadline = Date.now() + POLL_TIMEOUT_MS;
    let status = '';
    while (Date.now() < deadline) {
      status = sql(`SELECT status FROM orders WHERE order_no = '${orderNo}';`);
      if (status === 'CANCELLED') break;
      await new Promise((resolve) => setTimeout(resolve, 5_000));
    }
    expect(status, `${POLL_TIMEOUT_MS / 1000}s 内未自动取消（当前 ${status}）`).toBe('CANCELLED');

    // 4) 库存预占必须随取消释放
    expect(Number(sql(`SELECT locked_qty FROM sku_stock WHERE sku_id = ${skuId};`)),
      '取消订单必须把预占库存还回去').toBe(lockedBefore);
  } finally {
    // 若中途失败，也把遗留的待付款单走业务取消清掉，别锁住演示库存
    const leftover = sql(`SELECT status FROM orders WHERE order_no = '${orderNo}';`);
    if (leftover === 'WAIT_PAY') {
      await api(request, 'POST', `/orders/${orderNo}/cancel`, undefined, userToken);
    }
  }
});
