/**
 * 客服工作台「聊天消息里的订单卡片」改为当前页弹窗（TDD 红→绿）。
 *
 * 用户诉求（2026-11-16）：客服在使用客服对话功能时，点击聊天消息中的订单卡片
 * 不要跳转，直接在当前页面弹出该订单详情；且弹窗左上角可以关闭。
 *
 * 与 chat-order-dialog.spec.ts 的区别：那条覆盖的是右侧「顾客历史订单」侧栏；
 * 本条覆盖聊天消息气泡里的 .chat-order 卡片（ChatOrderCard 组件）。
 *
 * 断言口径：
 *   1) 客服侧看到客户发来的订单卡片；
 *   2) 点卡片「查看订单详情」→ URL 仍是 workbench（不跳页）；
 *   3) 当前页弹出订单详情弹窗，且显示该订单号；
 *   4) 弹窗左上角有 × 关闭按钮，点击后弹窗关闭。
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

async function apiCall(
  request: APIRequestContext, method: 'GET' | 'POST', suffix: string,
  data?: unknown, token?: string,
): Promise<any> {
  const res = await request.fetch(join(API_URL, suffix), {
    method,
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}
const apiPost = (r: APIRequestContext, s: string, d: unknown, t?: string) => apiCall(r, 'POST', s, d, t);
const apiGet = (r: APIRequestContext, s: string, t?: string) => apiCall(r, 'GET', s, undefined, t);

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

test('客服点聊天订单卡片：当前页弹详情且左上角可关闭，不跳页', async ({ browser, request }) => {
  test.setTimeout(180_000);
  const adminLogin = await apiPost(request, '/auth/login', {
    username: ADMIN_NAME, password: ADMIN_PASSWORD, aud: 'admin', deviceId: `e2e-carddlg-admin-${Date.now()}`,
  });
  const adminToken = adminLogin?.data?.accessToken as string;
  expect(adminToken).toBeTruthy();
  const userLogin = await apiPost(request, '/auth/login', {
    username: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD, aud: 'user', deviceId: `e2e-carddlg-user-${Date.now()}`,
  });
  const userToken = userLogin?.data?.accessToken as string;
  expect(userToken).toBeTruthy();

  // 取客户最近一笔订单作为卡片内容
  const orderPage = await apiGet(request, '/orders?page=1&size=1', userToken);
  const orderNo = orderPage?.data?.list?.[0]?.orderNo as string;
  expect(orderNo, '演示客户应至少有一笔订单').toMatch(/^SO/);

  // 清场：全员离线 + 关闭该客户未结束会话
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

  // 客户发一条 ORDER 摘要消息，客服侧会渲染成 .chat-order 卡片
  const cardContent = JSON.stringify({
    orderNo, status: 'WAIT_SHIP', payableCent: 20000, title: '订单协同测试商品', createdAt: Date.now(),
  });
  const sent = await apiPost(request, `/chat/sessions/${sessionId}/messages`, {
    senderType: 'CUSTOMER', msgType: 'ORDER', content: cardContent,
    clientMsgId: `e2e-card-${Date.now()}`,
  }, userToken);
  expect(sent?.code, `发订单卡片失败：${JSON.stringify(sent)}`).toBe(0);

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

    // 1) 聊天消息里的订单卡片
    const card = adminPage.locator('.message-line .chat-order').filter({ hasText: orderNo }).first();
    await expect(card, '客服侧应看到客户发来的订单卡片').toBeVisible({ timeout: 15_000 });

    // 2) 点卡片按钮：不跳页
    await card.locator('.chat-order__btn').click();
    await expect.poll(() => new URL(adminPage.url()).pathname, { timeout: 10_000 })
      .toBe('/workbench');

    // 3) 当前页弹详情，显示该订单号
    const dialog = adminPage.locator('.order-detail-dialog');
    await expect(dialog, '点卡片应在当前页弹订单详情').toBeVisible({ timeout: 15_000 });
    await expect(dialog).toContainText(orderNo);

    // 4) 左上角 × 关闭按钮可关闭弹窗
    const closeBtn = dialog.locator('.order-dlg-close');
    await expect(closeBtn, '弹窗左上角应有 × 关闭按钮').toBeVisible({ timeout: 10_000 });
    await closeBtn.click();
    await expect(dialog, '点左上角 × 应关闭弹窗').toBeHidden({ timeout: 10_000 });

    expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
  } finally {
    for (const staffId of STAFF_IDS) {
      await apiPost(request, '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
    }
    await adminCtx.close();
  }
});
