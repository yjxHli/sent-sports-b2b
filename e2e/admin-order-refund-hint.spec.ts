/**
 * 管理端订单列表「退款申请中」提示（2026-11-26，TDD 红→绿）。
 *
 * 用户诉求：客户申请退款后，管理端订单列表里对应的订单要提示「退款申请中」。
 * 真源口径：refund_order 的 PENDING / REFUNDING（与「我的售后」角标同口径），
 * 不读 orders.after_sale_status（该列只在退款完成时才写，申请阶段为空）。
 *
 * 本用例只读演示库：取一笔真实存在的在途退款单，在订单列表按订单号搜索并断言提示，
 * 再取一笔无在途退款的订单做反向断言。不写库，因此不需要自建自清。
 */
import { test, expect, type Page, type APIRequestContext } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

const join = (base: string, suffix: string): string =>
  `${base.replace(/\/+$/, '')}/${suffix.replace(/^\/+/, '')}`;

async function apiCall(
  request: APIRequestContext,
  method: 'GET' | 'POST',
  suffix: string,
  data?: unknown,
  token?: string,
): Promise<any> {
  const url = join(API_URL, suffix);
  const res = await request.fetch(url, {
    method,
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  const body: any = await res.json().catch(() => null);
  expect(body?.code, `${method} ${suffix} 失败：${JSON.stringify(body)}`).toBe(0);
  return body;
}

async function loginUserToken(request: APIRequestContext): Promise<string> {
  const body = await apiCall(request, 'POST', '/auth/login', {
    username: CUSTOMER_PHONE,
    password: CUSTOMER_PASSWORD,
    aud: 'user',
    deviceId: 'e2e-refund-hint',
  });
  return String(body?.data?.accessToken ?? '');
}

async function loginAdmin(page: Page): Promise<void> {
  await page.goto(join(ADMIN_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(ADMIN_NAME);
  await page.getByPlaceholder('密码').fill(ADMIN_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `管理端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

async function searchOrder(page: Page, orderNo: string): Promise<void> {
  const keywordInput = page.getByPlaceholder('订单号');
  await expect(keywordInput, '工具栏应有订单号搜索框').toBeVisible({ timeout: 20_000 });
  const searched = page.waitForResponse(
    (r) => r.url().includes('/api/admin/orders') && r.url().includes(`keyword=${orderNo}`),
    { timeout: 20_000 },
  );
  await keywordInput.fill(orderNo);
  await page.getByRole('button', { name: '查询' }).click();
  await searched;
  await expect(page.locator('.el-table__row'), `应搜到订单 ${orderNo}`).toHaveCount(1, {
    timeout: 20_000,
  });
}

test('申请退款的订单在管理端订单列表提示「退款申请中」，无在途退款的不提示', async ({
  page,
  request,
}) => {
  test.setTimeout(180_000);
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  const token = await loginUserToken(request);
  expect(token, '用户端应登录成功').toBeTruthy();

  // 1) 用「我的售后」在途单确定一笔真实申请过退款的订单（不写死演示单号）
  const refunds = await apiCall(request, 'GET', '/refunds?status=PENDING&page=1&size=50', undefined, token);
  const pendingOrders: string[] = (refunds?.data?.list ?? [])
    .map((r: any) => String(r.orderNo ?? ''))
    .filter((no: string) => no.startsWith('SO'));
  expect(pendingOrders.length, '演示客户应至少有一笔待审核退款单').toBeGreaterThan(0);
  const refundOrderNo = pendingOrders[0];

  // 对照单：同一客户名下没有在途退款的订单
  const orders = await apiCall(request, 'GET', '/orders?page=1&size=50', undefined, token);
  const normalOrderNo: string = (orders?.data?.list ?? [])
    .map((o: any) => String(o.orderNo ?? ''))
    .find((no: string) => no.startsWith('SO') && !pendingOrders.includes(no)) ?? '';
  expect(normalOrderNo, '演示客户应有一笔无在途退款的订单做对照').toBeTruthy();

  await loginAdmin(page);
  await page.goto(join(ADMIN_URL, '/order/list'));
  await expect(
    page.locator('.el-table th', { hasText: '售后提示' }),
    '订单列表应有售后提示列',
  ).toBeVisible({ timeout: 20_000 });

  await searchOrder(page, refundOrderNo);
  const hint = page.getByTestId('admin-order-refund-hint');
  await expect(hint, `订单 ${refundOrderNo} 应提示「退款申请中」`).toHaveText('退款申请中', {
    timeout: 20_000,
  });

  await searchOrder(page, normalOrderNo);
  await expect(
    page.getByTestId('admin-order-refund-hint'),
    `订单 ${normalOrderNo} 没有在途退款，不应提示`,
  ).toHaveCount(0, { timeout: 20_000 });

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});
