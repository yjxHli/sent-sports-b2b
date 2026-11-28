/**
 * 客服工作台：工作台已打开、接待完一轮后，下一位客户进线仍要自动弹出聊天框。
 *
 * 缺陷背景（2026-11-16 用户反馈）：
 *   「用 admin 登录 9001 在客服界面等待，商城测试账号发了个你好，并没有出现弹窗。」
 *   根因：自动开 Tab 用了**一次性开关** `autoOpened`——只要工作台启动时列表里已有
 *   会话（哪怕是上一轮遗留/刚接待完的），开关就被点亮，之后的新客户进线再也不弹。
 *   用户上一轮测试恰好留下 ACTIVE 会话（chat_session 336 等），于是必然复现。
 *
 * 断言口径：
 *   1) 工作台打开且列表为空 → 客户从商城页发消息 → Tab 自动打开（回归首轮能力）；
 *   2) 客服「结束会话」把 Tab 关掉后，下一位客户（新会话）进线 → Tab 仍要自动打开；
 *   3) 自动打开的 Tab 为当前激活，输入框就绪。
 */
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { clearOpenSessions, fillMinimalTicket } from './chat-helpers';

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

const join = (base: string, suffix: string): string =>
  `${base.replace(/\/+$/, '')}/${suffix.replace(/^\/+/, '')}`;

async function api(
  request: APIRequestContext,
  method: 'GET' | 'POST',
  path: string,
  data?: unknown,
  token?: string,
): Promise<any> {
  const res = await request.fetch(join(API_URL, path), {
    method,
    data,
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
  expect(body?.code, `管理端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/workbench/, { timeout: 20_000 });
  // 2026-11-21 起客服登录默认离线：真实客服也要手动切在线才开始接单，用例照做
  await page.locator('[data-testid="presence-online"]').click();
  await expect(page.locator('[data-testid="presence-online"]')).toHaveClass(/is-active/, { timeout: 15_000 });
}

async function loginCustomer(page: Page): Promise<void> {
  await page.goto(join(USER_URL, '/login'));
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
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
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

/**
 * 进「在线客服」页：只查当前会话，不建会话。
 * 会话号在首条消息发出时才产生，见 sendCustomerMessage。
 */
async function openCustomerChat(page: Page): Promise<void> {
  const current = page.waitForResponse(
    (r) => /\/api\/chat\/sessions\/current/.test(r.url()) && r.request().method() === 'GET',
    { timeout: 20_000 },
  );
  await page.goto(join(USER_URL, '/chat'));
  await current;
  await expect(page.getByPlaceholder('请输入想咨询的问题')).toBeEnabled({ timeout: 20_000 });
}

/** 首条消息触发建会话；返回本次会话号供工作台 Tab 断言 */
async function sendCustomerMessage(page: Page, text: string): Promise<string> {
  const created = page.waitForResponse(
    (r) => /\/api\/chat\/sessions$/.test(r.url()) && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  const box = page.getByPlaceholder('请输入想咨询的问题');
  await box.fill(text);
  await box.press('Enter');
  const sessionBody: any = await (await created).json().catch(() => null);
  const sessionId = Number(sessionBody?.data?.sessionId);
  expect(sessionId, `首条消息建会话失败：${JSON.stringify(sessionBody)}`).toBeGreaterThan(0);
  await expect(page.locator('.chat-bubble', { hasText: text }).last()).toBeVisible({ timeout: 15_000 });
  return String(sessionId);
}

test('工作台常驻时，每位新客户进线都要自动弹出聊天框', async ({ browser, request }) => {
  test.setTimeout(120_000);

  const adminLogin = await api(request, 'POST', '/auth/login', {
    username: ADMIN_NAME, password: ADMIN_PASSWORD, aud: 'admin',
    deviceId: `e2e-autoopen2-admin-${Date.now()}`,
  });
  const adminToken = adminLogin?.data?.accessToken as string;
  expect(adminToken).toBeTruthy();

  // 清场：全员离线 → 结束所有未结束会话（分页扫全量）→ 仅 9001 在线（单坐席）
  for (const staffId of STAFF_IDS) {
    await api(request, 'POST', '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
  }
  // 先把 9001 切在线再清场：离线坐席的 /sessions/open 不下发排队栏，且 close 只对 ACTIVE 生效，
  // 直接按离线队列去关，那两条 2026-06 的 WAITING 演示会话永远清不掉，
  // 9001 一上线就被补位成 ACTIVE，工作台开场就不空（自动弹窗断言必挂）。
  await api(request, 'POST', '/admin/chat/presence', { staffId: 9001, presence: 'ONLINE' }, adminToken);
  // clearOpenSessions 分页扫全量：演示库已 100+ 行，最早的僵尸会话（id=1）落在第 2 页之外
  await clearOpenSessions(request, adminToken);
  await api(request, 'POST', '/admin/chat/presence', { staffId: 9001, presence: 'ONLINE' }, adminToken);

  const adminCtx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const adminPage = await adminCtx.newPage();
  const userCtx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const userPage = await userCtx.newPage();

  try {
    // 1) 工作台先打开、此时列表为空
    await loginAdmin(adminPage);
    await adminPage.goto(join(CS_URL, '/workbench'));
    await expect(adminPage.locator('.el-tag', { hasText: 'WebSocket 已连接' })).toBeVisible({ timeout: 20_000 });
    await expect(adminPage.locator('.chat-panel'), '没有会话时聊天区应为空态').toHaveCount(0);

    // 前置校验：后端必须真的认为 9001 在线且有空位，否则客户进线只会排队、永远不会自动弹出
    const wl = await api(request, 'GET', '/admin/chat/workload', undefined, adminToken);
    const mine = (wl?.data ?? []).find((w: any) => Number(w.staffId) === 9001);
    expect(mine?.presence, '客户进线前 9001 在后端必须是在线').toBe('ONLINE');
    expect(Number(mine?.activeSessions), '客户进线前 9001 的名额应已清空').toBe(0);

    // 2) 第一位客户进线 → 聊天区自动打开
    await loginCustomer(userPage);
    await openCustomerChat(userPage);
    const firstSession = await sendCustomerMessage(userPage, '你好');
    // 后端口径：坐席在线且名额有空 → 机器人转人工的会话必须当场被接入。
    // 少了这一步，会话会停在 WAITING 占着队列，客户看着「前方还有 0 人」干等，工作台也没有可弹的会话。
    await expect.poll(async () => {
      const rows = await api(request, 'GET', '/admin/chat/sessions?page=1&size=5', undefined, adminToken);
      const row = (rows?.data?.list ?? []).find((r: any) => Number(r.sessionId) === Number(firstSession));
      return `${row?.status ?? ''}|${row?.staffId ?? ''}`;
    }, { timeout: 15_000, message: '客户进线后会话应被自动接入 9001' }).toBe('ACTIVE|9001');
    const firstPanel = adminPage.locator(`.chat-panel[data-session-id="${firstSession}"]`);
    await expect(firstPanel, '第一位客户进线应自动弹出聊天框').toBeVisible({ timeout: 25_000 });
    await expect(adminPage.locator('.chat-head__sub')).toContainText(`会话 #${firstSession}`);

    // 3) 客服结束该会话：先补最小工单过 V61 门禁，再结束
    await fillMinimalTicket(adminPage);
    await adminPage.getByRole('button', { name: '结束会话' }).click();
    const confirm = adminPage.locator('.el-message-box');
    await expect(confirm).toBeVisible({ timeout: 10_000 });
    await confirm.getByRole('button', { name: '确认结束' }).click();
    await expect(
      adminPage.locator('[data-testid="chat-ended-banner"]'),
      '结束后聊天区应显示本次会话已结束',
    ).toBeVisible({ timeout: 20_000 });
    const closedPanel = adminPage.locator('[data-testid="closed-panel"]');
    await expect(closedPanel, '左侧应保留一条已结束会话').toContainText(`会话 #${firstSession}`);
    await closedPanel.locator('[data-testid="close-ended"]').first().click();
    await expect(adminPage.locator('.chat-panel'), '客服主动关闭后聊天区才回到空态').toHaveCount(0, { timeout: 20_000 });

    // 4) 第二位客户（同一测试账号重新发首条消息 = 新会话）→ 仍要自动弹出
    await openCustomerChat(userPage);
    const secondSession = await sendCustomerMessage(userPage, '在吗');
    expect(secondSession, '重新进线应是一个新会话').not.toBe(firstSession);

    const secondPanel = adminPage.locator(`.chat-panel[data-session-id="${secondSession}"]`);
    await expect(secondPanel, '接待完一轮后，下一位客户进线仍应自动弹出聊天框').toBeVisible({ timeout: 25_000 });
    await expect(adminPage.locator('.chat-head__sub')).toContainText(`会话 #${secondSession}`);
    await expect(adminPage.getByPlaceholder('输入消息后回车发送'), '聊天输入框应自动就绪').toBeVisible({
      timeout: 15_000,
    });
  } finally {
    for (const staffId of STAFF_IDS) {
      await api(request, 'POST', '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
    }
    await adminCtx.close();
    await userCtx.close();
  }
});
