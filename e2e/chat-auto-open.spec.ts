/**
 * 客服工作台：第一个用户进线时自动弹出聊天框（TDD 红→绿）。
 *
 * 用户诉求（2026-11-16）：客服目前只需要一个 9001；如果是第一个用户进线，
 * 会直接弹出与用户的聊天框，无需手动点击左侧会话。
 *
 * 断言口径：
 *   1) 把 9001 设为唯一在线坐席（单坐席），客户进线建会话 → ACTIVE 分配 9001；
 *   2) 客服打开工作台，无需点左侧会话 → 该会话的 Tab 自动打开且为当前激活；
 *   3) 聊天框（消息输入区）自动就绪，可直接回复；
 *   4) 全程无 console error。
 */
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { clearOpenSessions, startConsultation } from './chat-helpers';

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

async function api(request: APIRequestContext, method: 'GET' | 'POST', s: string, d?: unknown, t?: string): Promise<any> {
  const res = await request.fetch(join(API_URL, s), {
    method, data: d, headers: t ? { Authorization: `Bearer ${t}` } : {}, failOnStatusCode: false,
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
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code).toBe(0);
  await expect(page).toHaveURL(/\/workbench/, { timeout: 20_000 });
  // 2026-11-21 起客服登录默认离线：真实客服也要手动切在线才开始接单，用例照做
  await page.locator('[data-testid="presence-online"]').click();
  await expect(page.locator('[data-testid="presence-online"]')).toHaveClass(/is-active/, { timeout: 15_000 });
}

test('单个用户进线：工作台自动弹出该会话聊天框，无需手动点击', async ({ browser, request }) => {
  test.setTimeout(180_000);
  const adminLogin = await api(request, 'POST', '/auth/login', {
    username: ADMIN_NAME, password: ADMIN_PASSWORD, aud: 'admin', deviceId: `e2e-autoopen-admin-${Date.now()}`,
  });
  const adminToken = adminLogin?.data?.accessToken as string;
  expect(adminToken).toBeTruthy();
  const userLogin = await api(request, 'POST', '/auth/login', {
    username: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD, aud: 'user', deviceId: `e2e-autoopen-user-${Date.now()}`,
  });
  const userToken = userLogin?.data?.accessToken as string;
  expect(userToken).toBeTruthy();

  // 清场：全部离线 → 9001 在线（离线坐席看不到排队，close 只对 ACTIVE 生效）→ 分页扫全量关掉未结束会话
  for (const staffId of STAFF_IDS) {
    await api(request, 'POST', '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
  }
  // 先把 9001 切在线再清场：离线坐席的 /sessions/open 不下发排队栏，且 close 只对 ACTIVE 生效，
  // 直接按离线队列去关，那两条 2026-06 的 WAITING 演示会话永远清不掉，
  // 9001 一上线就被补位成 ACTIVE，工作台开场就多出一个会话（自动弹窗断言必挂）。
  await api(request, 'POST', '/admin/chat/presence', { staffId: 9001, presence: 'ONLINE' }, adminToken);
  // clearOpenSessions 会分页扫全量：演示库已 100+ 行，最早的僵尸会话落在第 2 页之外
  await clearOpenSessions(request, adminToken);
  // 单坐席：只把 9001 设为在线，客户进线应自动分配给 9001（ACTIVE）
  await api(request, 'POST', '/admin/chat/presence', { staffId: 9001, presence: 'ONLINE' }, adminToken);
  const created = await api(request, 'POST', '/chat/sessions', { customerId: CUSTOMER_ID, vip: false }, userToken);
  const sessionId = Number(created?.data?.sessionId);
  // 业务口径：首条客户消息才算咨询，工作台只展示已有客户消息的会话
  await startConsultation(request, sessionId, userToken);
  expect(sessionId).toBeGreaterThan(0);
  expect(created?.data?.status, '单坐席在线时新会话应 ACTIVE').toBe('ACTIVE');
  expect(Number(created?.data?.staffId), '单坐席应分配给 9001').toBe(9001);

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

    // 关键：不点左侧会话，聊天区应自动打开该会话（2026-11-25 起顶部 Tab 条改为左侧列表切换）
    const panel = adminPage.locator(`.chat-panel[data-session-id="${sessionId}"]`);
    await expect(panel, '第一个用户进线应自动打开聊天窗，无需手动点击').toBeVisible({ timeout: 20_000 });
    await expect(adminPage.locator('.chat-head__sub'), '聊天区应指向该会话').toContainText(`会话 #${sessionId}`);

    // 聊天框就绪：消息输入区可见，右侧顾客历史订单侧栏也跟着该客户加载
    await expect(adminPage.getByPlaceholder('输入消息后回车发送'), '聊天输入框应自动就绪').toBeVisible({ timeout: 15_000 });
    await expect(adminPage.locator('.cust-orders'), '顾客历史订单侧栏应可见').toBeVisible({ timeout: 15_000 });

    expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
  } finally {
    for (const staffId of STAFF_IDS) {
      await api(request, 'POST', '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
    }
    await adminCtx.close();
  }
});
