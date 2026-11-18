/**
 * 「对方正在输入」双向提示：客户打字 → 客服端看到；客服打字 → 用户端看到。
 *
 * 后端：TYPING 帧只转发不落库（ChatWebSocketHandler.handleTyping），
 *      客户上行且会话 ACTIVE 才直投接待客服，否则走队列广播；客服上行直达客户。
 * 前端：两端各自 2s 节流上报，收到后亮 3s 自动熄灭。
 *
 * 断言口径：
 *   1) 客服端 Tab 出现「客户正在输入…」
 *   2) 用户端出现「客服正在回复中…」
 *   3) 双方都没有把 TYPING 当成消息渲染（气泡数量不变）
 */
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { startConsultation } from './chat-helpers';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';

const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const CUSTOMER_ID = Number(process.env.E2E_CUSTOMER_ID ?? 9002);
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';

/** 用例结束后必须全部置离线，否则会污染「未分配会话」前提（full-flow 断言接待客服是 9003）。 */
const STAFF_IDS = [9001, 9003, 9004, 9005, 9006, 9007];

const join = (base: string, path: string): string =>
  `${base.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;

async function apiPost(request: APIRequestContext, path: string, data: unknown, token?: string): Promise<any> {
  const res = await request.post(join(API_URL, path), {
    data,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
}

async function apiGet(request: APIRequestContext, path: string, token?: string): Promise<any> {
  const res = await request.get(join(API_URL, path), {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    failOnStatusCode: false,
  });
  return res.json().catch(() => null);
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

async function loginUser(page: Page): Promise<void> {
  await page.goto(join(USER_URL, '/login'));
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

test('双向「正在输入」：客户打字客服可见，客服打字客户可见', async ({ browser, request }) => {
  test.setTimeout(180_000);

  const adminLogin = await apiPost(request, '/auth/login', {
    username: ADMIN_NAME, password: ADMIN_PASSWORD, aud: 'admin', deviceId: `e2e-typing-admin-${Date.now()}`,
  });
  const adminToken = adminLogin?.data?.accessToken as string;
  expect(adminToken, `管理端 API 登录失败：${JSON.stringify(adminLogin)}`).toBeTruthy();

  const userLogin = await apiPost(request, '/auth/login', {
    username: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD, aud: 'user', deviceId: `e2e-typing-user-${Date.now()}`,
  });
  const userToken = userLogin?.data?.accessToken as string;
  expect(userToken, `用户端 API 登录失败：${JSON.stringify(userLogin)}`).toBeTruthy();

  // 1) 全员离线 → 关掉该客户未结束会话 → 新建会话落 WAITING
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
      await apiPost(request, `/admin/chat/sessions/${row.sessionId}/close`, { reason: 'DONE' }, adminToken);
    }
  }
  const created = await apiPost(request, '/chat/sessions', { customerId: CUSTOMER_ID, vip: false }, userToken);
  const sessionId = Number(created?.data?.sessionId);
  // 业务口径：首条客户消息才算咨询，工作台只展示已有客户消息的会话
  await startConsultation(request, sessionId, userToken);
  expect(sessionId, `建会话失败：${JSON.stringify(created)}`).toBeGreaterThan(0);
  expect(created?.data?.status, '前置条件：新会话应为 WAITING').toBe('WAITING');

  const userCtx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const adminCtx = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const userPage = await userCtx.newPage();
  const adminPage = await adminCtx.newPage();

  try {
    // 2) 客服先进工作台并点开该会话 Tab
    await loginAdmin(adminPage);
    await adminPage.goto(join(ADMIN_URL, '/chat/workbench'));
    await expect(adminPage.getByRole('heading', { name: '客服工作台' })).toBeVisible();
    await expect(
      adminPage.locator('.el-tag', { hasText: 'WebSocket 已连接' }),
      '客服工作台 WS 必须已连接',
    ).toBeVisible({ timeout: 20_000 });
    const item = adminPage.locator('.session-item', { hasText: `会话 #${sessionId}` }).first();
    await expect(item, `工作台左侧未出现会话 ${sessionId}`).toBeVisible({ timeout: 20_000 });
    await item.click();
    await expect(
      adminPage.locator('.el-tabs__item', { hasText: `#${sessionId}` }).first(),
    ).toBeVisible({ timeout: 10_000 });

    // 3) 客服先回一条，把会话接管为 ACTIVE（排队中用户端输入是禁用的）
    await adminPage.getByPlaceholder('输入消息后回车发送').fill('您好，这里是森特体育客服');
    await adminPage.getByRole('button', { name: '发送' }).last().click();
    await expect(
      adminPage.locator('.message-line.mine .bubble', { hasText: '森特体育客服' }).first(),
    ).toBeVisible({ timeout: 5_000 });

    // 4) 用户端打开客服页：接管后排队条应消失、输入解锁
    await loginUser(userPage);
    await userPage.goto(join(USER_URL, '/chat'));
    await expect(userPage.locator('.chat-state--on'), '用户端 WS 应在线').toBeVisible({ timeout: 20_000 });
    const userInput = userPage.getByPlaceholder('请输入想咨询的问题');
    await expect(userInput, '已接入后用户输入应解锁').toBeEnabled({ timeout: 20_000 });

    // 5) 客户打字（不发送）→ 客服端 3s 内显示「客户正在输入…」
    const adminBubbles = await adminPage.locator('.message-line').count();
    await userInput.fill('我想问一下定制球衣');
    await expect(
      adminPage.locator('.typing-hint'),
      '客户打字时客服端应显示「客户正在输入…」',
    ).toBeVisible({ timeout: 4_000 });
    await expect(adminPage.locator('.typing-hint')).toContainText('客户正在输入');
    expect(
      await adminPage.locator('.message-line').count(),
      'TYPING 帧不得被当成消息渲染',
    ).toBe(adminBubbles);

    // 6) 客服打字 → 用户端 4s 内显示「客服正在回复中…」
    // 等过节流窗口（两端都是 1s 节流），否则紧接着上一次按键的那一帧会被丢掉
    await userPage.waitForTimeout(1200);
    // 打字提示本身也是 .chat-msg（typing-row），统计真实气泡时要排除它
    const userBubbles = await userPage.locator('.chat-msg:not(.typing-row)').count();
    await adminPage.getByPlaceholder('输入消息后回车发送').fill('可以的，30 件起订');
    await expect(
      userPage.locator('.chat-bubble--typing'),
      '客服打字时用户端应显示「客服正在回复中…」',
    ).toBeVisible({ timeout: 4_000 });
    await expect(userPage.locator('.chat-bubble--typing')).toContainText('客服正在回复中');
    expect(
      await userPage.locator('.chat-msg:not(.typing-row)').count(),
      'TYPING 帧不得在用户端变成气泡',
    ).toBe(userBubbles);
  } finally {
    for (const staffId of STAFF_IDS) {
      await apiPost(request, '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
    }
    await userCtx.close();
    await adminCtx.close();
  }
});
