/**
 * 客服工作台点「顾客历史订单」→ 当前页弹窗而非跳页（TDD 红→绿）。
 *
 * 用户诉求（2026-11-16）：客服对话时点击旁边的订单不要跳转，
 * 而是在当前页面弹出该订单的详细弹窗。
 */
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { startConsultation , closeSessionWithTicket} from './chat-helpers';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CS_URL = process.env.E2E_CS_URL ?? 'http://127.0.0.1:5175';
// 客服工作台已剥离到独立工程 web-cs：登录后直落 /workbench，Token 前缀 cs-token_*
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const CUSTOMER_ID = Number(process.env.E2E_CUSTOMER_ID ?? 9002);
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';
const STAFF_IDS = [9001, 9003, 9004, 9005, 9006, 9007];
const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

const join = (base: string, suffix: string): string =>
  `${base.replace(/\/+$/, '')}/${suffix.replace(/^\/+/, '')}`;

async function apiPost(request: APIRequestContext, suffix: string, data: unknown, token?: string): Promise<any> {
  const res = await request.post(join(API_URL, suffix), {
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}
async function apiGet(request: APIRequestContext, suffix: string, token?: string): Promise<any> {
  const res = await request.get(join(API_URL, suffix), {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}
async function loginAdmin(page: Page): Promise<void> {
  await page.goto(join(CS_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(ADMIN_NAME);
  await page.getByPlaceholder('密码').fill(ADMIN_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  // 2026-11-28：登录页新增协议勾选（默认未勾选），E2E 必须先确认协议
  if (await page.getByTestId('auth-agree').count()) {
    await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  }
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code).toBe(0);
  await expect(page).toHaveURL(/\/workbench/, { timeout: 20_000 });
  // 2026-11-21 起客服登录默认离线：真实客服也要手动切在线才开始接单，用例照做
  await page.locator('[data-testid="presence-online"]').click();
  await expect(page.locator('[data-testid="presence-online"]')).toHaveClass(/is-active/, { timeout: 15_000 });
}

test('客服点顾客历史订单弹出订单详情弹窗且不跳页', async ({ browser, request }) => {
  test.setTimeout(180_000);
  const adminLogin = await apiPost(request, '/auth/login', {
    username: ADMIN_NAME, password: ADMIN_PASSWORD, aud: 'admin', deviceId: `e2e-dialog-admin-${Date.now()}`,
  });
  const adminToken = adminLogin?.data?.accessToken as string;
  expect(adminToken).toBeTruthy();
  const userLogin = await apiPost(request, '/auth/login', {
    username: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD, aud: 'user', deviceId: `e2e-dialog-user-${Date.now()}`,
  });
  const userToken = userLogin?.data?.accessToken as string;
  expect(userToken).toBeTruthy();

  for (const staffId of STAFF_IDS) {
    await apiPost(request, '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
  }
  // 清「所有」未结束会话：排队栏（/sessions/open）不受分页影响，能捞到 id 最小、落在列表最后一页的
  // 两条 2026-06 演示会话——服务端启动时 DemoDataGenerator 会把它们重置成 WAITING「待接入」
  const queue = await apiGet(request, '/admin/chat/sessions/open?staffId=9001', adminToken);
  const listed = await apiGet(request, '/admin/chat/sessions?page=1&size=100', adminToken);
  const openRows: any[] = [...(queue?.data?.waiting ?? []), ...(queue?.data?.active ?? []), ...(listed?.data?.list ?? [])];
  for (const row of openRows) {
    if (row.status !== 'CLOSED') {
      await closeSessionWithTicket(request, Number(row.sessionId), adminToken);
    }
  }
  const created = await apiPost(request, '/chat/sessions', { customerId: CUSTOMER_ID, vip: false }, userToken);
  const sessionId = Number(created?.data?.sessionId);
  // 业务口径：首条客户消息才算咨询，工作台只展示已有客户消息的会话
  await startConsultation(request, sessionId, userToken);
  expect(sessionId).toBeGreaterThan(0);

  const adminCtx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const adminPage = await adminCtx.newPage();
  const errors: string[] = [];
  adminPage.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  try {
    await loginAdmin(adminPage);
    await adminPage.goto(join(CS_URL, '/workbench'));
    await expect(adminPage.locator('.el-tag', { hasText: 'WebSocket 已连接' })).toBeVisible({ timeout: 20_000 });
    const sessionItem = adminPage.locator('.session-item', { hasText: `会话 #${sessionId}` }).first();
    await expect(sessionItem).toBeVisible({ timeout: 20_000 });
    await sessionItem.click();
    await expect(adminPage.locator(`.chat-panel[data-session-id="${sessionId}"]`)).toBeVisible({ timeout: 10_000 });

    // 用户要求（2026-11-25）：右侧改成三个平级 Tab，先切到「顾客订单」
    await adminPage.getByTestId('record-tab-orders').click();
    const firstOrder = adminPage.locator('.cust-orders .cust-order').first();
    await expect(firstOrder, '顾客历史订单侧栏应有数据').toBeVisible({ timeout: 20_000 });
    const orderNo = (await firstOrder.locator('.cust-order__no').innerText()).trim();

    await firstOrder.click();

    // 关键：不跳页，URL 仍是 workbench
    await expect.poll(() => new URL(adminPage.url()).pathname, { timeout: 10_000 }).toBe('/workbench');
    // 出现订单详情弹窗，且展示该订单号
    const dialog = adminPage.locator('.order-detail-dialog');
    await expect(dialog, '点订单应在当前页弹出订单详情弹窗').toBeVisible({ timeout: 15_000 });
    await expect(dialog).toContainText(orderNo);

    expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
  } finally {
    await adminCtx.close();
  }
});
