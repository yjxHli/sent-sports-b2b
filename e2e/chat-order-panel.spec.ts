/**
 * 客服咨询「订单协同」（TDD 红→绿，2026-11-15 用户诉求）。
 *
 * 用户原话：在客服咨询这里点击「查看订单」，会在本界面的旁边弹出订单列表，可以选择需要
 * 咨询的订单发给客服；同时客服也会看到用户的具体订单，客服聊天界面旁边会弹出顾客历史订单。
 *
 * 断言口径（每条都对应一个曾经不存在的链路）：
 *   1) 用户点「查看订单」→ 右侧出现 .chat-orders 侧栏，里面是本账号的订单（不跳页、不离开聊天）；
 *   2) 选中一单点「发给客服」→ 本条消息以 msgType=ORDER 发出，用户侧渲染成 .chat-order 卡片；
 *   3) 客服 Tab 内 3s 内出现同一张卡片（实时链路必须带 msgType，不能退化成一段 JSON 文本）；
 *   4) 客服工作台右侧 .cust-orders 侧栏按当前会话客户拉出历史订单（顾客历史购买订单）；
 *   5) 后端落库口径：chat_message.msg_type=ORDER，content 是含 orderNo 的摘要 JSON；
 *   6) 无 console error。
 */
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';

const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const CUSTOMER_ID = Number(process.env.E2E_CUSTOMER_ID ?? 9002);
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';

/** 需要显式置为离线的客服工号，确保用例从「排队 → 客服接管」这条干净路径开始 */
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

test('客服咨询可把订单发给客服，客服侧还能看到顾客历史订单', async ({ browser, request }) => {
  test.setTimeout(180_000);

  const adminLogin = await apiPost(request, '/auth/login', {
    username: ADMIN_NAME, password: ADMIN_PASSWORD, aud: 'admin', deviceId: `e2e-order-admin-${Date.now()}`,
  });
  const adminToken = adminLogin?.data?.accessToken as string;
  expect(adminToken, `管理端 API 登录失败：${JSON.stringify(adminLogin)}`).toBeTruthy();

  const userLogin = await apiPost(request, '/auth/login', {
    username: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD, aud: 'user', deviceId: `e2e-order-user-${Date.now()}`,
  });
  const userToken = userLogin?.data?.accessToken as string;
  expect(userToken, `用户端 API 登录失败：${JSON.stringify(userLogin)}`).toBeTruthy();

  // 清场：全员离线 + 关掉该客户所有未结束会话，保证新会话从 WAITING 开始
  for (const staffId of STAFF_IDS) {
    await apiPost(request, '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
  }
  const listed = await apiGet(request, '/admin/chat/sessions?page=1&size=100', adminToken);
  for (const row of listed?.data?.list ?? []) {
    if (Number(row.customerId) === CUSTOMER_ID && row.status !== 'CLOSED') {
      await apiPost(request, `/admin/chat/sessions/${row.sessionId}/close`, { reason: 'DONE' }, adminToken);
    }
  }
  const created = await apiPost(request, '/chat/sessions', { customerId: CUSTOMER_ID, vip: false }, userToken);
  const sessionId = Number(created?.data?.sessionId);
  expect(sessionId, `建会话失败：${JSON.stringify(created)}`).toBeGreaterThan(0);

  const userCtx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const adminCtx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const userPage = await userCtx.newPage();
  const adminPage = await adminCtx.newPage();

  const errors: string[] = [];
  userPage.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(`user: ${m.text()}`);
  });
  adminPage.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(`admin: ${m.text()}`);
  });

  try {
    // ---------- 1) 用户侧：展开订单侧栏 ----------
    await loginUser(userPage);
    await userPage.goto(join(USER_URL, '/chat'));
    await expect(userPage.locator('.chat-state--on'), '用户端 WS 应在线').toBeVisible({ timeout: 20_000 });

    await expect(userPage.locator('.chat-orders'), '订单侧栏默认应收起').toHaveCount(0);
    const ordersResp = userPage.waitForResponse(
      (r) => r.url().includes('/api/orders?') && r.request().method() === 'GET',
      { timeout: 20_000 },
    );
    await userPage.getByRole('button', { name: /查看订单/ }).first().click();
    await ordersResp;
    await expect(userPage.locator('.chat-orders'), '点「查看订单」应在旁边弹出订单列表').toBeVisible({
      timeout: 15_000,
    });
    const orderRows = userPage.locator('.chat-orders .chat-order-row');
    await expect(orderRows.first(), '订单侧栏应有本账号的订单').toBeVisible({ timeout: 20_000 });
    expect(await orderRows.count(), '侧栏只展示最近 10 笔').toBeLessThanOrEqual(10);
    // 不离开聊天页：URL 仍是 /chat
    expect(new URL(userPage.url()).pathname).toBe('/chat');

    // 排队中（客服还没接入）不允许发送：按钮禁用，但订单可以浏览
    await expect(
      orderRows.first().locator('.chat-order-row__send'),
      '排队中「发给客服」应禁用（和输入框一样，接入后才能发）',
    ).toBeDisabled();

    // ---------- 2) 客服接入（谁回复谁接待） ----------
    await loginAdmin(adminPage);
    await adminPage.goto(join(ADMIN_URL, '/chat/workbench'));
    await expect(
      adminPage.locator('.el-tag', { hasText: 'WebSocket 已连接' }),
      '客服工作台 WS 必须已连接',
    ).toBeVisible({ timeout: 20_000 });

    const sessionItem = adminPage.locator('.session-item', { hasText: `会话 #${sessionId}` }).first();
    await expect(sessionItem, `工作台左侧未出现会话 ${sessionId}`).toBeVisible({ timeout: 20_000 });
    await sessionItem.click();
    await expect(
      adminPage.locator('.el-tabs__item', { hasText: `#${sessionId}` }).first(),
    ).toBeVisible({ timeout: 10_000 });

    // 顾客历史订单侧栏：跟着当前 Tab 的客户走
    const custOrders = adminPage.locator('.cust-orders');
    await expect(custOrders, '聊天界面旁边应弹出顾客历史订单').toBeVisible({ timeout: 15_000 });
    await expect(
      custOrders.locator('.cust-order').first(),
      '顾客历史订单应有数据',
    ).toBeVisible({ timeout: 20_000 });
    await expect(custOrders, '侧栏应标明当前客户').toContainText(String(CUSTOMER_ID));

    await adminPage.getByPlaceholder('输入消息后回车发送').fill('您好，已收到您的订单，我看一下');
    await adminPage.getByRole('button', { name: '发送' }).last().click();
    await expect(
      userPage.getByPlaceholder('请输入想咨询的问题'),
      '客服接管后客户输入框应解锁',
    ).toBeEnabled({ timeout: 10_000 });

    // ---------- 3) 选中一单发给客服 ----------
    const firstRow = orderRows.first();
    const chosenNo = (await firstRow.locator('.chat-order-row__no').innerText()).trim();
    expect(chosenNo, '订单行应带订单号').toMatch(/^SO\d+/);
    await expect(
      firstRow.locator('.chat-order-row__send'),
      '接入后「发给客服」应可点',
    ).toBeEnabled({ timeout: 10_000 });
    await firstRow.locator('.chat-order-row__send').click();
    await expect(
      userPage.locator('.chat-msg--self .chat-order').first(),
      '发出的订单应渲染成订单卡片',
    ).toBeVisible({ timeout: 15_000 });
    await expect(
      userPage.locator('.chat-msg--self .chat-order').first(),
      '订单卡片应带订单号',
    ).toContainText(chosenNo);

    // ---------- 4) 客服侧看到同一张卡片 ----------
    await expect(
      adminPage.locator('.message-line .chat-order').first(),
      '客服应在 3s 内看到客户发来的订单卡片',
    ).toBeVisible({ timeout: 5000 });
    await expect(
      adminPage.locator('.message-line .chat-order').first(),
      '客服端卡片上的订单号应与客户发出的一致',
    ).toContainText(chosenNo);

    // ---------- 4) 落库口径 ----------
    const history = await apiGet(request, `/chat/sessions/${sessionId}/messages?afterSeq=0`, userToken);
    const orderMsgs = (history?.data ?? []).filter((m: any) => m.msgType === 'ORDER');
    expect(orderMsgs.length, `历史里应有 1 条订单卡片：${JSON.stringify(history?.data)}`).toBe(1);
    const payload = JSON.parse(String(orderMsgs[0].content));
    expect(payload.orderNo, '订单卡片 content 应是含 orderNo 的摘要 JSON').toBe(chosenNo);
    expect(Number(payload.payableCent)).toBeGreaterThanOrEqual(0);

    expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
  } finally {
    // 清场：工作台打开时会自动上报 ONLINE，不复位会让后续用例的会话被自动分配
    for (const staffId of STAFF_IDS) {
      await apiPost(request, '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
    }
    await userCtx.close();
    await adminCtx.close();
  }
});
