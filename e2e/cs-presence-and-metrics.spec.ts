/**
 * 客服坐席状态（2026-11-21 用户要求，TDD 红→绿）。
 *
 * 用户三条口径：
 *   1) 客服登录进客服系统后默认「离线」，离线看不到排队、也不能接线，切到在线才开始接人；
 *   2) 切「忙碌」时排队客户不会自动接入，要等切回在线；
 *   3) 客服指标页能实时看到每位客服的状态（在线/忙碌/离线），并可据此筛选。
 *
 * 断言全部落在「后端状态」上：光看 UI 变灰不够，会话必须真的还是 WAITING / 真的变 ACTIVE。
 */
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import {
  ADMIN_PASSWORD,
  ADMIN_URL,
  ADMIN_NAME,
  CUSTOMER_ID,
  CS_URL,
  apiCall,
  clearOpenSessions,
  goOnline,
  join,
  loginApi,
  setStaffPresence,
} from './chat-helpers';

const STAFF_IDS = [9001, 9003, 9004, 9005, 9006, 9007];
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

/** 客服端 UI 登录，但**不**自动切在线：本文件就是来验证「登录默认离线」的 */
async function loginWorkbench(page: Page): Promise<void> {
  await page.goto(join(CS_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(ADMIN_NAME);
  await page.getByPlaceholder('密码').fill(ADMIN_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `客服端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/workbench/, { timeout: 20_000 });
}

/** 后台登录（web-admin，:5174） */
async function loginAdmin(page: Page): Promise<void> {
  await page.goto(join(ADMIN_URL, 'login'));
  await page.waitForTimeout(800);
  if (page.url().includes('login')) {
    await page.getByPlaceholder('用户名').fill(ADMIN_NAME);
    await page.getByPlaceholder('密码').fill(ADMIN_PASSWORD);
    await page.getByRole('button', { name: /登录/ }).first().click();
    await page.waitForTimeout(1500);
  }
}

/** 从后台会话列表里读某条会话的真实状态（不信 UI，直接问库） */
async function sessionOf(request: APIRequestContext, adminToken: string, sessionId: number): Promise<any> {
  const body = await apiCall(request, 'GET', '/admin/chat/sessions?page=1&size=100', undefined, adminToken);
  return (body?.data?.list ?? []).find((row: any) => Number(row.sessionId) === sessionId) ?? null;
}

/** 造一条「客户已发消息」的排队会话；9001 非在线时应落 WAITING */
async function queueOneCustomer(
  request: APIRequestContext,
  userToken: string,
): Promise<{ sessionId: number; status: string; staffId: number | null }> {
  const created = await apiCall(request, 'POST', '/chat/sessions', {
    customerId: CUSTOMER_ID,
    vip: false,
  }, userToken);
  expect(created?.code, `建会话失败：${JSON.stringify(created)}`).toBe(0);
  const sessionId = Number(created?.data?.sessionId);
  expect(sessionId).toBeGreaterThan(0);
  // 业务口径：首条客户消息才算咨询，工作台列表只展示有客户消息的会话
  const sent = await apiCall(request, 'POST', `/chat/sessions/${sessionId}/messages`, {
    senderType: 'CUSTOMER',
    content: '你好，我想咨询一下球鞋尺码',
    clientMsgId: `e2e-presence-${sessionId}-${Date.now()}`,
  }, userToken);
  expect(sent?.code, `补首条消息失败：${JSON.stringify(sent)}`).toBe(0);
  return {
    sessionId,
    status: String(created?.data?.status ?? ''),
    staffId: created?.data?.staffId == null ? null : Number(created.data.staffId),
  };
}

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

test('登录默认离线：看不到排队、接不了线；切在线后队列出现并自动接入', async ({ browser, request }) => {
  test.setTimeout(180_000);
  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin', `e2e-presence-a-${Date.now()}`);
  const userToken = await loginApi(request, CUSTOMER_PHONE, CUSTOMER_PASSWORD, 'user', `e2e-presence-u-${Date.now()}`);

  await setStaffPresence(request, adminToken, STAFF_IDS, 'OFFLINE');
  await clearOpenSessions(request, adminToken);
  // 9001 离线时客户进线只能排队
  const queued = await queueOneCustomer(request, userToken);
  expect(queued.status, '9001 离线时新会话必须落 WAITING').toBe('WAITING');
  expect(queued.staffId, '离线坐席不应被分配会话').toBeNull();

  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const page = await ctx.newPage();
  const errors: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

  try {
    await loginWorkbench(page);
    // ① 顶栏默认选中「离线」
    await expect(page.locator('[data-testid="presence-offline"]'),
      '客服登录后默认必须是离线状态，不能一登录就接单').toHaveClass(/is-active/, { timeout: 15_000 });
    await expect(page.locator('[data-testid="seat-presence-hint"]')).toContainText('离线');

    // ② 离线看不到排队：接口不下发，页面也没有排队列表/排队提示（2026-11-25 排队列表已删）
    await expect(page.locator('[data-testid="queue-hint"]'),
      '离线时不应提示有人在排队').toHaveCount(0);
    await expect(page.locator('[data-testid="queue-panel"]'), '排队列表已按需求删除').toHaveCount(0);

    // ③ 离线不接线：会话仍是 WAITING
    expect((await sessionOf(request, adminToken, queued.sessionId))?.status).toBe('WAITING');

    // ④ 切在线：后端把排队客户补位进来，聊天区自动打开该会话
    await goOnline(page);
    const panel = page.locator(`.chat-panel[data-session-id="${queued.sessionId}"]`);
    await expect(panel, '切在线后排队客户应自动接入并弹出聊天窗').toBeVisible({ timeout: 20_000 });
    await expect(page.locator('[data-testid="queue-hint"]'),
      '客户已被补位，不应再显示排队人数').toHaveCount(0);

    await expect.poll(async () => (await sessionOf(request, adminToken, queued.sessionId))?.status,
      { timeout: 20_000 }).toBe('ACTIVE');
    expect(Number((await sessionOf(request, adminToken, queued.sessionId))?.staffId)).toBe(9001);

    expect(errors.filter((e) => !/Failed to load resource|net::ERR/i.test(e)),
      `console errors: ${errors.join(' | ')}`).toHaveLength(0);
  } finally {
    await clearOpenSessions(request, adminToken).catch(() => undefined);
    await setStaffPresence(request, adminToken, STAFF_IDS, 'OFFLINE').catch(() => undefined);
    await ctx.close();
  }
});

test('忙碌：排队客户可见但不自动接入，切回在线才补位', async ({ browser, request }) => {
  test.setTimeout(180_000);
  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin', `e2e-presence-b-${Date.now()}`);
  const userToken = await loginApi(request, CUSTOMER_PHONE, CUSTOMER_PASSWORD, 'user', `e2e-presence-bu-${Date.now()}`);

  await setStaffPresence(request, adminToken, STAFF_IDS, 'OFFLINE');
  await clearOpenSessions(request, adminToken);
  // 9001 忙碌时客户进线：不能自动分配
  await setStaffPresence(request, adminToken, [9001], 'BUSY');
  const queued = await queueOneCustomer(request, userToken);
  expect(queued.status, '忙碌坐席不应自动接单').toBe('WAITING');

  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const page = await ctx.newPage();

  try {
    await loginWorkbench(page);
    // 登录会复位成离线（后端也落库），这里直接切忙碌
    await page.locator('[data-testid="presence-busy"]').click();
    await expect(page.locator('[data-testid="presence-busy"]')).toHaveClass(/is-active/, { timeout: 15_000 });
    await expect(page.locator('[data-testid="seat-presence-hint"]')).toContainText('忙碌');

    // 忙碌可见排队人数（不清空），但不该自动弹出会话 / 不该被分配
    await expect(page.locator('[data-testid="queue-hint"]'), '忙碌时仍应提示有人在排队')
      .toBeVisible({ timeout: 15_000 });
    await expect(page.locator('[data-testid="queue-hint"]')).toContainText('另有');
    await page.waitForTimeout(4000);
    await expect(page.locator('.chat-panel'), '忙碌时不能自动接入排队客户').toHaveCount(0);
    expect((await sessionOf(request, adminToken, queued.sessionId))?.status,
      '忙碌时后端也不该把会话分配出去').toBe('WAITING');

    // 切回在线 → 自动补位
    await goOnline(page);
    await expect.poll(async () => (await sessionOf(request, adminToken, queued.sessionId))?.status,
      { timeout: 20_000 }).toBe('ACTIVE');
    await expect(page.locator(`.chat-panel[data-session-id="${queued.sessionId}"]`),
      '切回在线后该会话应自动接入并显示聊天窗').toBeVisible({ timeout: 20_000 });
  } finally {
    await clearOpenSessions(request, adminToken).catch(() => undefined);
    await setStaffPresence(request, adminToken, STAFF_IDS, 'OFFLINE').catch(() => undefined);
    await ctx.close();
  }
});

test('客服指标：实时显示坐席状态，并可按状态筛选', async ({ browser, request }) => {
  test.setTimeout(180_000);
  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin', `e2e-metrics-p-${Date.now()}`);

  // 造出三种状态各一个：9001 在线、9003 忙碌、其余离线
  await setStaffPresence(request, adminToken, STAFF_IDS, 'OFFLINE');
  await setStaffPresence(request, adminToken, [9001], 'ONLINE');
  await setStaffPresence(request, adminToken, [9003], 'BUSY');

  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const page = await ctx.newPage();

  try {
    await loginAdmin(page);
    await page.goto(join(ADMIN_URL, 'chat/metrics'));
    await page.waitForSelector('[data-testid="metrics-table"] .el-table__body tbody tr', { timeout: 25_000 });
    await page.waitForTimeout(1500);

    const statusCells = page.locator('[data-testid="metrics-presence"]');
    await expect(statusCells.first(), '指标表必须有「状态」列').toBeVisible({ timeout: 15_000 });
    const texts = await statusCells.allInnerTexts();
    expect(texts.length, '每位客服一行，都应有状态').toBeGreaterThanOrEqual(7);
    expect(texts.some((t) => t.includes('在线'))).toBeTruthy();
    expect(texts.some((t) => t.includes('忙碌'))).toBeTruthy();
    expect(texts.some((t) => t.includes('离线'))).toBeTruthy();

    // 顶部汇总卡：状态分布一眼可见
    const summary = page.locator('[data-testid="metrics-presence-summary"]');
    await expect(summary).toContainText('在线 1');
    await expect(summary).toContainText('忙碌 1');

    // 筛选「忙碌」：只剩 9003 一行
    await page.locator('[data-testid="metrics-presence-filter"]').click();
    await page
      .locator('.el-select-dropdown:visible .el-select-dropdown__item')
      .filter({ hasText: '忙碌' })
      .first()
      .click();
    await page.waitForTimeout(800);

    const rows = page.locator('[data-testid="metrics-table"] .el-table__body tbody tr');
    await expect(rows, '筛选忙碌后应只剩 1 位客服').toHaveCount(1);
    await expect(rows.first()).toContainText('9003');
    await expect(page.locator('[data-testid="metrics-presence"]').first()).toContainText('忙碌');

    // 重置：清空状态筛选，行数恢复全员
    await page.locator('[data-testid="metrics-reset"]').click();
    await page.waitForTimeout(1200);
    await expect(statusCells.first(), '重置后应恢复全员').toBeVisible();
    expect((await statusCells.allInnerTexts()).length).toBeGreaterThanOrEqual(7);
  } finally {
    await setStaffPresence(request, adminToken, STAFF_IDS, 'OFFLINE').catch(() => undefined);
    await ctx.close();
  }
});
