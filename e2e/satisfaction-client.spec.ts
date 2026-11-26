/**
 * 客服时间分隔 + 评价卡实时到客户端（2026-11-17 用户第 2 轮反馈 1、2，TDD）。
 *
 * 用户原话：
 *   1)「客服界面时间存在问题」（截图里首条消息上方是「1970年01月01日 08:00」）；
 *   2)「点击发送邀请评价，客户端无内容，无法评价，结束会话也是」。
 *
 * 根因：
 *   1) 工作台拉历史时读的是 `item.createdAt`，后端 `ChatDtos.MessageVO` 字段却是
 *      `createdAtMillis` → 整段历史时间恒为 0（1970-01-01）；
 *   2) `WsChatNotifier.messagePublished` 对已分配会话只 `broadcast(staffId)`，
 *      SYSTEM 评价卡永远送不到客户连接上，客户要等下次拉历史才看到。
 *
 * 断言口径：
 *   1) 刷新工作台（走历史接口）后，时间分隔显示的是「今天 HH:mm」而非 1970；
 *   2) 客服点「请评价」后，**客户不刷新**即可看到评价卡并打分，客服端随之显示档位。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { startConsultation , closeSessionWithTicket} from './chat-helpers';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CS_URL = process.env.E2E_CS_URL ?? 'http://127.0.0.1:5175';
// 客服工作台已剥离到独立工程 web-cs：登录后直落 /workbench，Token 前缀 cs-token_*
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');

const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const CUSTOMER_ID = Number(process.env.E2E_CUSTOMER_ID ?? 9002);
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';
const STAFF_IDS = [9001, 9003, 9004, 9005, 9006, 9007];
const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';

const MSG_CUSTOMER = '在吗，我想问下定制球衣的印号';

const join = (base: string, path: string): string =>
  `${base.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;

function sql(statement: string): string {
  const bin = process.env.E2E_MYSQL_BIN ?? (existsSync(LOCAL_MYSQL) ? LOCAL_MYSQL : 'mysql');
  return execFileSync(bin, ['-h127.0.0.1', '-P3306', '-uroot',
    `-p${process.env.E2E_DB_PASSWORD ?? '123456'}`, 'sent_dev', '-N', '-B', '-e', statement],
    { encoding: 'utf8' }).trim();
}

async function api(request: APIRequestContext, method: 'GET' | 'POST', path: string,
                   data?: unknown, token?: string): Promise<any> {
  const res = await request.fetch(`${API_URL}${path}`, {
    method, data, headers: token ? { Authorization: `Bearer ${token}` } : {},
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

async function openSessionTab(page: Page, sessionId: number, timeout = 30_000): Promise<void> {
  const item = page.locator('.session-item', { hasText: `会话 #${sessionId}` }).first();
  await expect(item, `工作台左侧未出现会话 ${sessionId}`).toBeVisible({ timeout });
  await item.click();
  await expect(
    page.locator(`.chat-panel[data-session-id="${sessionId}"]`),
    `会话 ${sessionId} 应打开对应 Tab`,
  ).toBeVisible({ timeout: 10_000 });
}

test('工作台历史时间不再是 1970，客服发评价卡后客户不刷新即可打分', async ({ browser, request }) => {
  test.setTimeout(240_000);

  const adminLogin = await api(request, 'POST', '/auth/login', {
    username: ADMIN_NAME, password: ADMIN_PASSWORD, aud: 'admin',
    deviceId: `e2e-sat-client-admin-${Date.now()}`,
  });
  const adminToken = adminLogin?.data?.accessToken as string;
  expect(adminToken, `管理端登录失败：${JSON.stringify(adminLogin)}`).toBeTruthy();

  const userLogin = await api(request, 'POST', '/auth/login', {
    username: CUSTOMER_PHONE, password: CUSTOMER_PASSWORD, aud: 'user',
    deviceId: `e2e-sat-client-user-${Date.now()}`,
  });
  const userToken = userLogin?.data?.accessToken as string;
  expect(userToken, `用户端登录失败：${JSON.stringify(userLogin)}`).toBeTruthy();

  // 清场：只留 9001 在线 + 关掉该客户所有未结束会话（避免复用旧会话、避免工作台被别的 Tab 干扰）
  for (const staffId of STAFF_IDS) {
    await api(request, 'POST', '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
  }
  const queue = await api(request, 'GET', '/admin/chat/sessions/open?staffId=9001', undefined, adminToken);
  const listed = await api(request, 'GET', '/admin/chat/sessions?page=1&size=100', undefined, adminToken);
  const openRows: any[] = [...(queue?.data?.waiting ?? []), ...(queue?.data?.active ?? []),
    ...(listed?.data?.list ?? [])];
  for (const row of openRows) {
    if (row.status !== 'CLOSED') {
      await closeSessionWithTicket(request, Number(row.sessionId), adminToken);
    }
  }
  await api(request, 'POST', '/admin/chat/presence', { staffId: 9001, presence: 'ONLINE' }, adminToken);

  const created = await api(request, 'POST', '/chat/sessions',
    { customerId: CUSTOMER_ID, vip: false }, userToken);
  const sessionId = Number(created?.data?.sessionId);
  // 业务口径：首条客户消息才算咨询，工作台只展示已有客户消息的会话
  await startConsultation(request, sessionId, userToken);
  expect(sessionId, `建会话失败：${JSON.stringify(created)}`).toBeGreaterThan(0);

  const userCtx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const adminCtx = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const userPage = await userCtx.newPage();
  const adminPage = await adminCtx.newPage();

  try {
    // ---------- 客户：进客服页并发一条消息（历史里就有真实时间戳了） ----------
    await loginUser(userPage);
    await userPage.goto(join(USER_URL, '/chat'));
    const input = userPage.getByPlaceholder('请输入想咨询的问题');
    await expect(input, '客户聊天输入框应可用（会话已接入）').toBeEditable({ timeout: 30_000 });
    await input.fill(MSG_CUSTOMER);
    await userPage.getByRole('button', { name: '发送' }).click();
    await expect(
      userPage.locator('.chat-bubble', { hasText: MSG_CUSTOMER }).first(),
      '客户消息应出现在自己的聊天窗',
    ).toBeVisible({ timeout: 20_000 });

    // ---------- 客服：进工作台，打开该会话 Tab ----------
    await loginAdmin(adminPage);
    await adminPage.goto(join(CS_URL, '/workbench'));
    await openSessionTab(adminPage, sessionId);

    // 刷新工作台：这一步让消息全部走「历史接口」加载，正是 1970 的复现场景
    await adminPage.reload();
    await openSessionTab(adminPage, sessionId);
    await expect(
      adminPage.locator('.bubble', { hasText: MSG_CUSTOMER }).first(),
      '历史消息应显示客户刚才那句话',
    ).toBeVisible({ timeout: 20_000 });

    const divider = adminPage.getByTestId('chat-time-divider').first();
    await expect(divider, '首条消息上方应显示时间分隔').toBeVisible({ timeout: 15_000 });
    const dividerText = (await divider.innerText()).trim();
    expect(dividerText, `时间分隔不能出现 1970：${dividerText}`).not.toContain('1970');
    expect(dividerText, `时间分隔不能带年份（跨年才带）：${dividerText}`).not.toContain('年');
    expect(dividerText, `时间分隔应是「今天 HH:mm」：${dividerText}`).toMatch(/^\d{2}:\d{2}$/);

    // 与真实发送时间对齐：HH:mm 允许与当前时间相差 1 分钟（跨分钟边界的抖动）
    const nowMinutes = new Date().getHours() * 60 + new Date().getMinutes();
    const [dh, dm] = dividerText.split(':').map((n) => Number(n));
    const shown = dh * 60 + dm;
    expect(Math.abs(nowMinutes - shown), `时间分隔 ${dividerText} 与实际发送时间偏差过大`)
      .toBeLessThanOrEqual(1);

    // ---------- 客服点「请评价」→ 客户不刷新就能看到卡 ----------
    await adminPage.getByTestId('send-satisfaction').click();
    const card = userPage.getByTestId('satisfaction-card');
    await expect(card, '客户不刷新也应收到评价卡（WS 直达客户连接）')
      .toBeVisible({ timeout: 20_000 });
    await expect(card).toContainText('请评价本次服务');

    // ---------- 客户打分：卡就地变「已评价」 ----------
    await userPage.getByTestId('satisfaction-satisfied').click();
    await expect(userPage.getByTestId('satisfaction-card'), '打分后卡片应显示档位')
      .toContainText('满意', { timeout: 20_000 });
    await expect(userPage.getByTestId('satisfaction-satisfied')).toHaveCount(0);

    // 服务端确实落了评价
    expect(sql(`SELECT score FROM chat_satisfaction WHERE session_id = ${sessionId};`))
      .toBe('SATISFIED');

    // 客服端卡片也应随 WS 更新帧变成「本次服务评价：满意」
    await expect(
      adminPage.getByTestId('satisfaction-card').first(),
      '客服端卡片应显示客户打的档位',
    ).toContainText('满意', { timeout: 20_000 });
  } finally {
    sql(`DELETE FROM chat_satisfaction WHERE session_id = ${sessionId};`);
    for (const staffId of STAFF_IDS) {
      await api(request, 'POST', '/admin/chat/presence', { staffId, presence: 'OFFLINE' }, adminToken);
    }
    await userCtx.close();
    await adminCtx.close();
  }
});
