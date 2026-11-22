/**
 * 客服实时会话回归：客户 ↔ 客服双向「不刷新即可收到」+ 排队提示。
 *
 * 缺陷背景（2026-11-13 用户反馈）：
 *   客服发的消息用户能第一时间收到，但客户发的消息客服端要刷新才看得到。
 *   根因是「未分配（WAITING）会话」的客户消息后端以 QUEUE 广播，
 *   而管理端 useChatSocket 只认 QUEUE + CONNECTED，把业务 QUEUE 帧整体丢弃。
 *
 * 并发调度升级后（同日晚）：WAITING 时用户端禁用输入并显示「前方还有 X 人」，
 * 因此本用例的客户消息改走 REST 发送（后端语义相同），重点仍是
 * 「未接待/未接入的会话，客服 Tab 里也能实时看到客户消息」。
 *
 * 覆盖：
 *   1) 用户端 WAITING → 显示排队提示、输入框禁用
 *   2) 客服工作台点开排队会话（不点接待）→ 客户消息 3s 内出现在 Tab 里
 *   3) 客服在 Tab 内直接回复 → 后端自动接管（谁回复谁接待）→ 用户端排队提示消失、输入解锁
 */
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { startConsultation } from './chat-helpers';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CS_URL = process.env.E2E_CS_URL ?? 'http://127.0.0.1:5175';
// 客服工作台已剥离到独立工程 web-cs：登录后直落 /workbench，Token 前缀 cs-token_*
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';

const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const CUSTOMER_ID = Number(process.env.E2E_CUSTOMER_ID ?? 9002);
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';

/** 需要显式置为离线的客服工号，确保新建会话不会按「在线人数」自动分配。 */
const STAFF_IDS = [9001, 9003, 9004, 9005, 9006, 9007];

const MSG_USER = '请问这个球能印校徽吗（未接待实时性）';
const MSG_STAFF = '可以的，请提供校徽矢量图';
const MSG_USER_AGAIN = '好的，我这就上传（接管后实时性）';

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

async function loginAdmin(page: Page, username: string, password: string): Promise<void> {
  await page.goto(join(CS_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(username);
  await page.getByPlaceholder('密码').fill(password);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `管理端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/workbench/, { timeout: 20_000 });
  // 2026-11-21 起客服登录默认离线：真实客服也要手动切在线才开始接单，用例照做
  await page.locator('[data-testid="presence-online"]').click();
  await expect(page.locator('[data-testid="presence-online"]')).toHaveClass(/is-active/, { timeout: 15_000 });
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

/** 工作台左侧列表里按会话号点开 Tab（行元素带 .session-item）。 */
async function openSessionTab(page: Page, sessionId: number, timeout = 20_000) {
  const item = page.locator('.session-item', { hasText: `会话 #${sessionId}` }).first();
  await expect(item, `工作台左侧未出现会话 ${sessionId}`).toBeVisible({ timeout });
  await item.click();
  await expect(
    page.locator('.el-tabs__item', { hasText: `#${sessionId}` }).first(),
    `会话 ${sessionId} 应打开对应 Tab`,
  ).toBeVisible({ timeout: 10_000 });
}

test('未接待会话：排队提示 + 客户消息实时到达客服 + 客服回复自动接管', async ({ browser, request }) => {
  test.setTimeout(180_000);

  const adminLogin = await apiPost(request, '/auth/login', {
    username: ADMIN_NAME, password: ADMIN_PASSWORD, aud: 'admin', deviceId: `e2e-realtime-admin-${Date.now()}`,
  });
  const adminToken = adminLogin?.data?.accessToken as string;
  expect(adminToken, `管理端 API 登录失败：${JSON.stringify(adminLogin)}`).toBeTruthy();

  const userLogin = await apiPost(request, '/auth/login', {
    username: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD, aud: 'user', deviceId: `e2e-realtime-user-${Date.now()}`,
  });
  const userToken = userLogin?.data?.accessToken as string;
  expect(userToken, `用户端 API 登录失败：${JSON.stringify(userLogin)}`).toBeTruthy();

  // 1) 全员置离线，保证新建会话落 WAITING（否则会被自动分配给在线客服）
  for (const staffId of STAFF_IDS) {
    await apiPost(request, '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
  }

  // 2) 关闭该客户所有未结束会话，避免复用旧会话（复用会直接 ACTIVE → 测不到 QUEUE 路径）
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

  // 3) 用户建会话：必须停在 WAITING 且无接待客服
  const created = await apiPost(request, '/chat/sessions', { customerId: CUSTOMER_ID, vip: false }, userToken);
  const sessionId = Number(created?.data?.sessionId);
  // 业务口径：首条客户消息才算咨询，工作台只展示已有客户消息的会话
  await startConsultation(request, sessionId, userToken);
  expect(sessionId, `建会话失败：${JSON.stringify(created)}`).toBeGreaterThan(0);
  expect(created?.data?.status, '前置条件：新会话应为 WAITING（无人在线时）').toBe('WAITING');

  const userCtx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const adminCtx = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const userPage = await userCtx.newPage();
  const adminPage = await adminCtx.newPage();

  try {
    // 4) 用户进入客服页：应看到排队提示且输入框禁用
    await loginUser(userPage);
    await userPage.goto(join(USER_URL, '/chat'));
    await expect(userPage.locator('.chat-state--on'), '用户端 WS 应在线').toBeVisible({ timeout: 20_000 });
    await expect(userPage.locator('.queue-banner'), 'WAITING 时应显示排队提示').toBeVisible({ timeout: 20_000 });
    await expect(userPage.locator('.queue-banner')).toContainText('前方还有');
    await expect(userPage.getByPlaceholder('排队中，接入后可发送消息'), '排队中输入框应禁用').toBeDisabled();

    // 5) 客服进入工作台（WS 已连接），点开排队会话的 Tab（不点「接待」）
    await loginAdmin(adminPage, ADMIN_NAME, ADMIN_PASSWORD);
    await adminPage.goto(join(CS_URL, '/workbench'));
    await expect(adminPage.getByRole('heading', { name: '客服工作台' })).toBeVisible();
    await expect(
      adminPage.locator('.el-tag', { hasText: 'WebSocket 已连接' }),
      '客服工作台 WS 必须已连接（否则无法实时收消息）',
    ).toBeVisible({ timeout: 20_000 });
    await openSessionTab(adminPage, sessionId);

    // 6) 客户消息（REST 发送，排队中用户端输入被禁用）→ 客服 Tab 3s 内收到
    const sent = await apiPost(request, `/chat/sessions/${sessionId}/messages`, {
      senderType: 'CUSTOMER', content: MSG_USER, clientMsgId: `e2e-realtime-c-${Date.now()}`,
    }, userToken);
    expect(sent?.code, `客户发消息失败：${JSON.stringify(sent)}`).toBe(0);
    await expect(
      adminPage.locator('.message-line .bubble', { hasText: MSG_USER }).first(),
      '客服端应在 3s 内实时收到客户消息（未接待时走 QUEUE 实时投递）',
    ).toBeVisible({ timeout: 3000 });

    // 7) 客服在 Tab 内直接回复 → 后端自动接管（谁回复谁接待）
    await adminPage.getByPlaceholder('输入消息后回车发送').fill(MSG_STAFF);
    await adminPage.getByRole('button', { name: '发送' }).last().click();
    await expect(
      adminPage.locator('.message-line.mine .bubble', { hasText: MSG_STAFF }).first(),
      '客服端应显示自己发出的消息',
    ).toBeVisible({ timeout: 5000 });
    await expect(
      userPage.locator('.chat-msg--other .chat-bubble', { hasText: MSG_STAFF }).first(),
      '用户端应实时收到客服回复',
    ).toBeVisible({ timeout: 5000 });

    // 8) 接管后：排队提示消失、输入解锁
    await expect(userPage.locator('.queue-banner'), '客服接入后排队提示应消失').toBeHidden({ timeout: 5000 });
    await expect(userPage.getByPlaceholder('请输入想咨询的问题')).toBeEnabled({ timeout: 5000 });
    await expect(userPage.locator('.chat-system'), '应提示「客服已接入」').toContainText('客服已接入');

    const openBody = await apiGet(request, `/admin/chat/sessions/open?staffId=9001`, adminToken);
    const takenOver = [...(openBody?.data?.active ?? []), ...(openBody?.data?.waiting ?? [])]
      .find((row: any) => Number(row.sessionId) === sessionId);
    expect(takenOver?.status, '客服回复未分配会话后，会话应自动转为 ACTIVE').toBe('ACTIVE');
    expect(Number(takenOver?.staffId), '会话应自动接管给回复的客服（admin=9001）').toBe(9001);

    // 9) 接管后客户再发消息：走 MSG 直投，仍 3s 内到达
    const before = await adminPage.locator('.message-line').count();
    const sentAgain = await apiPost(request, `/chat/sessions/${sessionId}/messages`, {
      senderType: 'CUSTOMER', content: MSG_USER_AGAIN, clientMsgId: `e2e-realtime-c2-${Date.now()}`,
    }, userToken);
    expect(sentAgain?.code).toBe(0);
    await expect(
      adminPage.locator('.message-line'),
      '接管后客户消息应 3s 内直达客服（ACTIVE 直投，无需刷新）',
    ).toHaveCount(before + 1, { timeout: 3000 });
  } finally {
    // 清场：工作台打开时会自动上报 ONLINE，若不复位，后续用例新建会话会被自动分配给 9001，
    // 破坏「未分配会话」前提（full-flow 断言接待客服为 9003）。
    for (const staffId of STAFF_IDS) {
      await apiPost(request, '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
    }
    await userCtx.close();
    await adminCtx.close();
  }
});
