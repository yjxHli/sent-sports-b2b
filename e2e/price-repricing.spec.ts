/**
 * 商品价格整体下调到 10~40 元 + 定制加收 10 元/件（2026-11-17 用户需求 5）。
 *
 * 背景：V33 只改了 9201~9209 九个演示 SKU，其余 111 个仍是 55~995 元的旧价，
 * 商城端与「全部下调到 10~40」的口径不一致（V35 补齐）。
 *
 * 断言口径：
 *   1) 商城列表接口返回的每个 SKU 零售价都在 [1000, 4000] 分；
 *   2) 商品详情页展示价也在 10~40 元；
 *   3) 定制单费用快照 = 10 元 × 件数（不是 0、也不是历史乱值）；
 *   4) cost < floor <= retail（否则毛利算出来是负的）。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type APIRequestContext } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');
const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';

function mysqlBin(): string {
  if (process.env.E2E_MYSQL_BIN) return process.env.E2E_MYSQL_BIN;
  return existsSync(LOCAL_MYSQL) ? LOCAL_MYSQL : 'mysql';
}

function sql(statement: string): string {
  return execFileSync(mysqlBin(), [
    '-h127.0.0.1', '-P3306', '-uroot', `-p${process.env.E2E_DB_PASSWORD ?? '123456'}`,
    'sent_dev', '-N', '-B', '-e', statement,
  ], { encoding: 'utf8' }).trim();
}

async function userToken(request: APIRequestContext): Promise<string> {
  const res = await request.fetch(`${API_URL}/auth/login`, {
    method: 'POST',
    data: {
      username: process.env.E2E_USER_NAME ?? '13800138000',
      password: process.env.E2E_USER_PASSWORD ?? '123456',
      aud: 'user',
      deviceId: `e2e-price-${Date.now()}`,
    },
    failOnStatusCode: false,
  });
  const body: any = await res.json().catch(() => null);
  const token = body?.data?.accessToken as string;
  expect(token, `用户登录失败：${JSON.stringify(body)}`).toBeTruthy();
  return token;
}

test('商品零售价全部落在 10~40 元，且 cost < floor <= retail', async ({ request }) => {
  test.setTimeout(120_000);
  const token = await userToken(request);

  const collected: Array<{ productId: string; skuId: string; retail: number }> = [];
  for (let page = 1; page <= 5; page += 1) {
    const res = await request.fetch(`${API_URL}/products?page=${page}&size=20`, {
      headers: { Authorization: `Bearer ${token}` },
      failOnStatusCode: false,
    });
    const body: any = await res.json().catch(() => null);
    expect(body?.code, `商品列表失败：${JSON.stringify(body)}`).toBe(0);
    const list = body?.data?.list ?? [];
    if (!list.length) break;
    for (const p of list) {
      for (const sku of p.skus ?? []) {
        const retail = Number(sku?.price?.retailPriceCent ?? 0);
        if (retail > 0) {
          collected.push({ productId: String(p.productId ?? p.id), skuId: String(sku.skuId), retail });
        }
      }
    }
  }

  expect(collected.length, '应有可售 SKU 参与校验').toBeGreaterThan(0);
  const outOfRange = collected.filter((s) => s.retail < 1000 || s.retail > 4000);
  expect(
    outOfRange,
    `以下 SKU 零售价不在 10~40 元：${JSON.stringify(outOfRange.slice(0, 8))}`,
  ).toHaveLength(0);

  // 成本/底价必须构成合法毛利链，否则报表毛利为负
  const broken = sql(
    'SELECT COUNT(*) FROM sku WHERE deleted = 0 '
    + 'AND NOT (cost_price_cent < floor_price_cent AND floor_price_cent <= retail_price_cent);',
  );
  expect(Number(broken), 'cost < floor <= retail 必须成立').toBe(0);
});

test('商品详情页展示价在 10~40 元', async ({ page, request }) => {
  test.setTimeout(120_000);
  const token = await userToken(request);
  const res = await request.fetch(`${API_URL}/products?page=1&size=20`, {
    headers: { Authorization: `Bearer ${token}` },
    failOnStatusCode: false,
  });
  const body: any = await res.json().catch(() => null);
  const first = body?.data?.list?.[0];
  test.skip(!first, '演示库没有商品，跳过');

  await page.goto(`${USER_URL}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(process.env.E2E_USER_NAME ?? '13800138000');
  await page.getByPlaceholder('请输入密码').fill(process.env.E2E_USER_PASSWORD ?? '123456');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)/, { timeout: 20_000 });

  const productId = String(first.productId ?? first.id);
  await page.goto(`${USER_URL}/product/${productId}`);
  const intPart = page.locator('.buy__price .buy__num').first();
  await expect(intPart).toBeVisible({ timeout: 20_000 });
  const yuan = Number((await intPart.innerText()).trim());
  console.log('详情页展示价整数部分:', yuan);
  expect(yuan, `详情页价格 ${yuan} 元不在 10~40 元`).toBeGreaterThanOrEqual(10);
  expect(yuan).toBeLessThanOrEqual(40);
});

test('定制单费用快照 = 10 元 × 件数', async () => {
  // 只看未取消/未退款的订单：已退款单的金额是历史事实，V35 有意不改写
  const broken = sql(
    'SELECT COUNT(*) FROM custom_order co '
    + 'JOIN order_item oi ON oi.id = co.order_item_id '
    + 'JOIN orders o ON o.id = oi.order_id '
    + "WHERE o.status NOT IN ('CANCELLED', 'CLOSED') "
    + "AND o.pay_status NOT IN ('FULL_REFUNDED', 'PART_REFUNDED') "
    + 'AND co.fee_cent <> 1000 * co.qty;',
  );
  expect(Number(broken), '存在定制费与「10 元/件」不符的定制单').toBe(0);
});
