/**
 * 客服双向图片消息（TDD 红→绿）。
 *
 * 用户诉求：「客服交流的时候，客户和客服都可以发送图片」。
 *
 * 实现口径：
 *   - 图片先走 `POST /api/files` 传成 fileId，消息体只带 fileId（content），
 *     `msg_type=IMAGE` 落到 `chat_message`，不让二进制进消息表；
 *   - 实时链路（WS MSG 帧）与历史接口（afterSeq 补拉）都要带 msgType，
 *     否则刷新页面后图片会退化成一段路径字符串。
 *
 * 断言口径：
 *   1) 客户在客服页选图发送 → 客服工作台 Tab 内 3s 出现 <img> 气泡；
 *   2) 客服在工作台选图发送 → 客户页面 3s 出现 <img> 气泡；
 *   3) 双方刷新后（走历史接口）图片依然是图片，而不是路径文本。
 */
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { startConsultation } from './chat-helpers';
import path from 'node:path';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CS_URL = process.env.E2E_CS_URL ?? 'http://127.0.0.1:5175';
// 客服工作台已剥离到独立工程 web-cs：登录后直落 /workbench，Token 前缀 cs-token_*
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';

const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const CUSTOMER_ID = Number(process.env.E2E_CUSTOMER_ID ?? 9002);
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';

/** 需要显式置为离线的客服工号，确保用例从「排队 → 客服接管」这条干净路径开始。 */
const STAFF_IDS = [9001, 9003, 9004, 9005, 9006, 9007];

/** 测试素材：e2e/fixtures 下的真实 PNG（Playwright 以 CJS 运行，__dirname 可用） */
const CHAT_IMAGE = path.join(__dirname, 'fixtures', 'chat-shot.png');

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

test('客服双向发图：客户发的图客服看得到，客服发的图客户看得到', async ({ browser, request }) => {
  test.setTimeout(180_000);

  const adminLogin = await apiPost(request, '/auth/login', {
    username: ADMIN_NAME, password: ADMIN_PASSWORD, aud: 'admin', deviceId: `e2e-img-admin-${Date.now()}`,
  });
  const adminToken = adminLogin?.data?.accessToken as string;
  expect(adminToken, `管理端 API 登录失败：${JSON.stringify(adminLogin)}`).toBeTruthy();

  const userLogin = await apiPost(request, '/auth/login', {
    username: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD, aud: 'user', deviceId: `e2e-img-user-${Date.now()}`,
  });
  const userToken = userLogin?.data?.accessToken as string;
  expect(userToken, `用户端 API 登录失败：${JSON.stringify(userLogin)}`).toBeTruthy();

  // 1) 清场：全员离线 + 关掉该客户所有未结束会话，保证新会话从 WAITING 开始
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

  const userCtx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const adminCtx = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const userPage = await userCtx.newPage();
  const adminPage = await adminCtx.newPage();

  try {
    // 2) 客户进客服页；客服进工作台并点开该会话的 Tab
    await loginUser(userPage);
    await userPage.goto(join(USER_URL, '/chat'));
    await expect(userPage.locator('.chat-state--on'), '用户端 WS 应在线').toBeVisible({ timeout: 20_000 });

    await loginAdmin(adminPage);
    await adminPage.goto(join(CS_URL, '/workbench'));
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

    // 3) 客服先回一句话完成接管（排队中客户输入框是禁用的）
    await adminPage.getByPlaceholder('输入消息后回车发送').fill('您好，请把实物照片发我看看');
    await adminPage.getByRole('button', { name: '发送' }).last().click();
    await expect(
      userPage.getByPlaceholder('请输入想咨询的问题'),
      '客服接管后客户输入框应解锁',
    ).toBeEnabled({ timeout: 10_000 });

    // 4) 客户发图 → 客服 Tab 内 3s 内出现图片气泡
    await userPage.locator('.chat-input__tools .tool').nth(1).click();
    await userPage.locator('.chat-input input[type=file]').setInputFiles(CHAT_IMAGE);
    await expect(
      adminPage.locator('.message-line .bubble-img').first(),
      '客服端应在 3s 内看到客户发来的图片',
    ).toBeVisible({ timeout: 5000 });
    const customerImgSrc = await adminPage.locator('.message-line .bubble-img').first().getAttribute('src');
    expect(customerImgSrc, `图片地址应指向文件服务：${customerImgSrc}`).toContain('/api/files/');

    // 5) 客服发图 → 客户页面 3s 内出现图片气泡
    await adminPage.getByRole('button', { name: /图片/ }).click();
    await adminPage.locator('.hidden-input').setInputFiles(CHAT_IMAGE);
    await expect(
      userPage.locator('.chat-msg--other .chat-img').first(),
      '客户应实时收到客服发来的图片',
    ).toBeVisible({ timeout: 5000 });

    // 6) 刷新后（走历史接口）图片依然是图片
    await userPage.reload();
    await expect(
      userPage.locator('.chat-img').first(),
      '刷新后历史里的图片消息仍应渲染成图片（msgType 必须随历史接口返回）',
    ).toBeVisible({ timeout: 20_000 });

    // 7) 后端落库口径：chat_message.msg_type=IMAGE，content 是 fileId
    const history = await apiGet(request, `/chat/sessions/${sessionId}/messages?afterSeq=0`, userToken);
    const imageMsgs = (history?.data ?? []).filter((m: any) => m.msgType === 'IMAGE');
    expect(imageMsgs.length, `历史里应有 2 条图片消息：${JSON.stringify(history?.data)}`).toBe(2);
    for (const m of imageMsgs) {
      expect(String(m.content), `图片消息的 content 应是 fileId：${m.content}`).toMatch(/^\d{4}\/\d{2}\//);
    }
  } finally {
    // 清场：工作台打开时会自动上报 ONLINE，不复位会让后续用例的会话被自动分配
    for (const staffId of STAFF_IDS) {
      await apiPost(request, '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
    }
    await userCtx.close();
    await adminCtx.close();
  }
});
