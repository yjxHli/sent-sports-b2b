/**
 * 单坐席恢复正常（TDD 红→绿，2026-11-16 用户实测）。
 *
 * 现象：商城测试账号发「你好」，9001 客服界面「进行中 / 排队中」都是 0，刷新管理端也没有。
 * 根因：客户复用了上一轮遗留的 **ACTIVE + staff_id=9003** 的会话；工作台会话列表按
 *       staff_id 过滤，9001 永远看不到 —— 消息等于发进黑洞。
 * 修复：CreateSessionUseCase 复用路径做单坐席纠正（9001 在线时把会话交还 9001）。
 *
 * 断言口径：
 *   1) 会话被 9003 接走（模拟历史遗留）后，9001 的工作台看不到它；
 *   2) 客户重新进入商城客服页（复用会话）→ 接口返回的接待客服变成 9001；
 *   3) 9001 打开工作台：左侧「进行中」出现该会话，且聊天 Tab 自动弹出。
 */
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { startConsultation } from './chat-helpers';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CS_URL = process.env.E2E_CS_URL ?? 'http://127.0.0.1:5175';
// 客服工作台已剥离到独立工程 web-cs：登录后直落 /workbench，Token 前缀 cs-token_*
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';
const CUSTOMER_ID = Number(process.env.E2E_CUSTOMER_ID ?? 9002);
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
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
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `管理端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/workbench/, { timeout: 20_000 });
  // 2026-11-21 起客服登录默认离线：真实客服也要手动切在线才开始接单，用例照做
  await page.locator('[data-testid="presence-online"]').click();
  await expect(page.locator('[data-testid="presence-online"]')).toHaveClass(/is-active/, { timeout: 15_000 });
}

test('会话被其他客服占住时，客户再进线应交还 9001 并在工作台可见', async ({ browser, request }) => {
  test.setTimeout(180_000);

  const adminLogin = await api(request, 'POST', '/auth/login', {
    username: ADMIN_NAME, password: ADMIN_PASSWORD, aud: 'admin',
    deviceId: `e2e-handover-admin-${Date.now()}`,
  });
  const adminToken = adminLogin?.data?.accessToken as string;
  expect(adminToken).toBeTruthy();
  const userLogin = await api(request, 'POST', '/auth/login', {
    username: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD, aud: 'user',
    deviceId: `e2e-handover-user-${Date.now()}`,
  });
  const userToken = userLogin?.data?.accessToken as string;
  expect(userToken).toBeTruthy();

  // 清场：全员离线 → 结束该客户未结束会话 → 9003 在线、9001 离线
  for (const staffId of STAFF_IDS) {
    await api(request, 'POST', '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
  }
  // 清「所有」未结束会话：排队栏（/sessions/open）不受分页影响，能捞到 id 最小、落在列表最后一页的
  // 两条 2026-06 演示会话——服务端启动时 DemoDataGenerator 会把它们重置成 WAITING「待接入」
  const queue = await api(request, 'GET', '/admin/chat/sessions/open?staffId=9001', undefined, adminToken);
  const listed = await api(request, 'GET', '/admin/chat/sessions?page=1&size=100', undefined, adminToken);
  const openRows: any[] = [...(queue?.data?.waiting ?? []), ...(queue?.data?.active ?? []), ...(listed?.data?.list ?? [])];
  for (const row of openRows) {
    if (row.status !== 'CLOSED') {
      await api(request, 'POST', `/admin/chat/sessions/${row.sessionId}/close`, { reason: 'DONE' }, adminToken);
    }
  }
  await api(request, 'POST', '/admin/chat/presence', { staffId: 9003, presence: 'ONLINE' }, adminToken);

  // 1) 客户进线：单坐席只认 9001，9001 离线 → 会话进排队
  const created = await api(request, 'POST', '/chat/sessions', { customerId: CUSTOMER_ID, vip: false }, userToken);
  const sessionId = Number(created?.data?.sessionId);
  // 业务口径：首条客户消息才算咨询，工作台只展示已有客户消息的会话
  await startConsultation(request, sessionId, userToken);
  expect(sessionId).toBeGreaterThan(0);
  expect(created?.data?.status, '9001 离线时应排队而不是分给 9003').toBe('WAITING');

  // 2) 模拟历史遗留：9003 把这条会话接走（等价于上一轮测试用 9003 接待后没结束）
  const accepted = await api(request, 'POST', `/admin/chat/sessions/${sessionId}/accept`, { staffId: 9003 }, adminToken);
  expect(accepted?.code, `9003 接待失败：${JSON.stringify(accepted)}`).toBe(0);
  expect(Number(accepted?.data?.staffId)).toBe(9003);

  // 3) 此时 9001 的工作台列表看不到这条会话（缺陷现场）
  const beforeHandover = await api(
    request, 'GET', `/admin/chat/sessions/open?staffId=9001`, undefined, adminToken,
  );
  const visibleBefore = [...(beforeHandover?.data?.active ?? []), ...(beforeHandover?.data?.waiting ?? [])]
    .some((row: any) => Number(row.sessionId) === sessionId);
  expect(visibleBefore, '前置条件：会话挂在 9003 名下时 9001 看不到').toBe(false);

  // 4) 9001 上线，客户重新进入客服页（复用同一条会话）→ 应自动交还 9001
  await api(request, 'POST', '/admin/chat/presence', { staffId: 9001, presence: 'ONLINE' }, adminToken);
  const reused = await api(request, 'POST', '/chat/sessions', { customerId: CUSTOMER_ID, vip: false }, userToken);
  expect(Number(reused?.data?.sessionId), '复用同一会话，不该新建').toBe(sessionId);
  expect(reused?.data?.reused).toBe(true);
  expect(Number(reused?.data?.staffId), '被其他客服占住的会话应交还 9001').toBe(9001);

  // 5) 9001 打开工作台：进行中能看到这条会话，Tab 自动弹出
  const adminCtx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const adminPage = await adminCtx.newPage();
  try {
    await loginAdmin(adminPage);
    await adminPage.goto(join(CS_URL, '/workbench'));
    await expect(adminPage.locator('.el-tag', { hasText: 'WebSocket 已连接' })).toBeVisible({ timeout: 20_000 });

    await expect(
      adminPage.locator('.list-panel').first().locator('.session-item', { hasText: `会话 #${sessionId}` }),
      '交还后 9001 的「进行中」应出现该会话',
    ).toBeVisible({ timeout: 20_000 });
    await expect(
      adminPage.locator('.el-tabs__item', { hasText: `#${sessionId}` }).first(),
      '该会话的聊天框应自动打开',
    ).toBeVisible({ timeout: 20_000 });
  } finally {
    await api(request, 'POST', `/admin/chat/sessions/${sessionId}/close`, { reason: 'DONE' }, adminToken).catch(() => undefined);
    for (const staffId of STAFF_IDS) {
      await api(request, 'POST', '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
    }
    await adminCtx.close();
  }
});
