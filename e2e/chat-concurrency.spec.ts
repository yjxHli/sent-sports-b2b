/**
 * 并发限制 / 排队提示 / 自动补位（后台调度核心链路）。
 *
 * 场景（一条用例串起来，全部走真库 + 真 WS）：
 *   1) 只让 9001 在线；连续建 3 个客户会话 → 全部 ACTIVE，挂到 9001（并发上限 3）
 *   2) 第 4 位客户进线 → WAITING，排队位置 = 1；用户端显示「前方还有 0 人」的排队条并禁用输入
 *   3) 工作台左侧：进行中 3 / 排队中 1；右侧最多 3 个 Tab，点第 4 个被拒并提示
 *   4) 在 Tab 内「结束会话」→ 后端自动补位：排队第一位转 ACTIVE/9001、负载仍为 3、
 *      用户端排队条消失（收到 ASSIGNED）
 */
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { startConsultation, closeSessionWithTicket, fillMinimalTicket } from './chat-helpers';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CS_URL = process.env.E2E_CS_URL ?? 'http://127.0.0.1:5175';
// 客服工作台已剥离到独立工程 web-cs：登录后直落 /workbench，Token 前缀 cs-token_*
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';

const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';
const STAFF_ID = Number(process.env.E2E_CONCURRENCY_STAFF ?? 9001);
/** 充场客户：真实存在但无人登录，用来占满 3 个接待位 */
const FILLER_CUSTOMERS = [91001, 91002, 91003];
/** 第 4 位客户：需要真实登录看排队条，用 9002（13800138000） */
const QUEUED_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const QUEUED_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const QUEUED_CUSTOMER = Number(process.env.E2E_CUSTOMER_ID ?? 9002);

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

async function loginQueuedCustomer(page: Page): Promise<void> {
  await page.goto(join(USER_URL, '/login'));
  await page.getByPlaceholder('请输入11位手机号').fill(QUEUED_PHONE);
  await page.getByPlaceholder('请输入密码').fill(QUEUED_PASSWORD);
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
 * 清空所有未结束会话（关闭 ACTIVE 会自动补位，所以循环到没有为止）。
 *
 * 必须逐页翻完：列表按 created_at 倒序且 `size` 上限 100，而演示数据里沉淀了几百条历史
 * 会话，最早那两条 WAITING（2026-06 生成）会永远落在第 2 页之外。漏掉它们的话，
 * 「排队位置 = 第 1 位」会被抬高成长度随机的数字（实测变成 3）。
 */
async function resetOpenSessions(request: APIRequestContext, adminToken: string): Promise<void> {
  for (let round = 0; round < 30; round += 1) {
    let closed = 0;
    for (let page = 1; page <= 30; page += 1) {
      const listed = await apiGet(request, `/admin/chat/sessions?page=${page}&size=100`, adminToken);
      const list: any[] = listed?.data?.list ?? [];
      const open = list.filter((row: any) => row.status !== 'CLOSED');
      for (const row of open) {
        await closeSessionWithTicket(request, Number(row.sessionId), adminToken);
        closed += 1;
      }
      const total = Number(listed?.data?.total ?? 0);
      if (list.length === 0 || page * 100 >= total) break;
    }
    if (closed === 0) return;
  }
  throw new Error('会话清场失败：30 轮后仍有未结束会话');
}

test('并发上限 3 + 排队位置 + 关一个自动补位（多 Tab 上限 3）', async ({ browser, request }) => {
  test.setTimeout(240_000);

  const adminLogin = await apiPost(request, '/auth/login', {
    username: ADMIN_NAME, password: ADMIN_PASSWORD, aud: 'admin', deviceId: `e2e-conc-admin-${Date.now()}`,
  });
  const adminToken = adminLogin?.data?.accessToken as string;
  expect(adminToken, `管理端 API 登录失败：${JSON.stringify(adminLogin)}`).toBeTruthy();

  const userLogin = await apiPost(request, '/auth/login', {
    username: QUEUED_PHONE, password: QUEUED_PASSWORD, aud: 'user', deviceId: `e2e-conc-user-${Date.now()}`,
  });
  const userToken = userLogin?.data?.accessToken as string;
  expect(userToken, `用户端 API 登录失败：${JSON.stringify(userLogin)}`).toBeTruthy();

  // 1) 清场 + 只让 9001 在线（负载会按库里 ACTIVE 数校准为 0）。
  //    别的坐席若还留着 ONLINE（上一批用例登录过工作台就会上报 ONLINE），
  //    新建会话会被分给它们，本用例断言的「三个客户全归 9001」就不成立。
  await resetOpenSessions(request, adminToken);
  const roster = await apiGet(request, '/admin/chat/workload', adminToken);
  const otherStaffIds = (roster?.data ?? [])
    .map((row: any) => Number(row.staffId))
    .filter((id: number) => Number.isFinite(id) && id !== STAFF_ID);
  for (const id of otherStaffIds) {
    await apiPost(request, '/admin/chat/presence', { staffId: id, presence: 'OFFLINE' }, adminToken);
  }
  await apiPost(request, '/admin/chat/presence', { staffId: STAFF_ID, presence: 'ONLINE' }, adminToken);

  // 2) 占满 3 个接待位
  const activeIds: number[] = [];
  for (const customerId of FILLER_CUSTOMERS) {
    const created = await apiPost(request, '/chat/sessions', { customerId, vip: false }, userToken);
    expect(created?.code, `建会话失败（客户 ${customerId}）：${JSON.stringify(created)}`).toBe(0);
    expect(created?.data?.status, `客户 ${customerId} 应被直接分配为 ACTIVE`).toBe('ACTIVE');
    expect(Number(created?.data?.staffId), `客户 ${customerId} 应分配给 9001`).toBe(STAFF_ID);
    activeIds.push(Number(created.data.sessionId));
    await startConsultation(request, activeIds[activeIds.length - 1], userToken);
  }

  // 3) 第 4 位客户 → 排队
  const queued = await apiPost(request, '/chat/sessions', { customerId: QUEUED_CUSTOMER, vip: false }, userToken);
  const queuedSessionId = Number(queued?.data?.sessionId);
  await startConsultation(request, queuedSessionId, userToken);
  expect(queued?.data?.status, '第 4 位客户应进入排队（WAITING）').toBe('WAITING');
  const queueVo = await apiGet(request, `/chat/sessions/${queuedSessionId}/queue`, userToken);
  expect(queueVo?.data?.status).toBe('WAITING');
  expect(queueVo?.data?.position, '排队位置应为第 1 位').toBe(1);

  const userCtx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const adminCtx = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const userPage = await userCtx.newPage();
  const adminPage = await adminCtx.newPage();

  try {
    // 4) 用户端：排队条出现、输入禁用
    await loginQueuedCustomer(userPage);
    await userPage.goto(join(USER_URL, '/chat'));
    await expect(userPage.locator('.queue-banner'), '排队客户应看到排队提示').toBeVisible({ timeout: 20_000 });
    await expect(userPage.locator('.queue-banner')).toContainText('前方还有 0 人');
    await expect(userPage.getByPlaceholder('排队中，接入后可发送消息')).toBeDisabled();

    // 5) 工作台：左侧只剩「进行中 3」，排队只在顶部留一行提示（2026-11-25 用户要求删掉排队列表）
    await loginAdmin(adminPage, ADMIN_NAME, ADMIN_PASSWORD);
    await adminPage.goto(join(CS_URL, '/workbench'));
    await expect(
      adminPage.locator('.el-tag', { hasText: 'WebSocket 已连接' }),
      '客服工作台 WS 必须已连接',
    ).toBeVisible({ timeout: 20_000 });

    const activePanel = adminPage.locator('[data-testid="active-panel"]');
    await expect(activePanel.locator('.count'), '进行中应为 3').toHaveText('3', { timeout: 20_000 });
    await expect(adminPage.locator('[data-testid="queue-panel"]'), '排队列表已按需求删除').toHaveCount(0);
    await expect(
      adminPage.locator('[data-testid="queue-hint"]'),
      '顶部应提示还有客户在排队（在线即自动接待，队列不再列表化）',
    ).toContainText('另有');

    // 逐个点左侧会话切换：聊天区始终只有一个面板（顶部 Tab 条已删除）
    for (let i = 0; i < 3; i += 1) {
      await activePanel.locator('.session-item').nth(i).click();
      await expect(adminPage.locator('.chat-panel'), '单会话视图只应有一个聊天面板').toHaveCount(1);
    }
    await expect(
      adminPage.locator('.wb__chip', { hasText: '我的并发' }).first(),
      '并发名额应显示 3/3',
    ).toContainText('3/3');

    // 6) 结束当前会话 → 自动补位排队客户
    // 顶栏「今日完成接待」是本次新增指标：结束一个会话后必须 +1（按本地自然日统计）
    const todayBefore = Number((await adminPage.locator('.today-stat__num').innerText()).trim());
    expect(Number.isFinite(todayBefore), '工作台顶栏应显示今日完成接待数').toBe(true);

    await fillMinimalTicket(adminPage);
    await adminPage.getByRole('button', { name: '结束会话' }).first().click();
    await adminPage.locator('.el-message-box').getByRole('button', { name: '确认结束' }).click();
    await expect(
      adminPage.locator('.el-message', { hasText: '已自动接入排队客户' }),
      '结束会话后应提示已自动接入排队客户',
    ).toBeVisible({ timeout: 10_000 });

    // 7) 断言后排客户已转 ACTIVE / 9001，且负载仍为 3
    await expect.poll(async () => {
      const open = await apiGet(request, `/admin/chat/sessions/open?staffId=${STAFF_ID}`, adminToken);
      const target = [...(open?.data?.active ?? []), ...(open?.data?.waiting ?? [])]
        .find((row: any) => Number(row.sessionId) === queuedSessionId);
      return `${target?.status ?? ''}|${target?.staffId ?? ''}`;
    }, { timeout: 20_000, message: '排队客户应被自动补位为 ACTIVE/9001' })
      .toBe(`ACTIVE|${STAFF_ID}`);

    await expect.poll(async () => Number((await adminPage.locator('.today-stat__num').innerText()).trim()), {
      timeout: 15_000,
      message: '结束一个会话后「今日完成接待」应 +1',
    }).toBe(todayBefore + 1);

    const workload = await apiGet(request, '/admin/chat/workload', adminToken);
    const mine = (workload?.data ?? []).find((w: any) => Number(w.staffId) === STAFF_ID);
    expect(mine?.activeSessions, '关一个补一个，客服负载应保持为 3').toBe(3);
    expect(mine?.maxSessions, '并发上限默认值应为 3').toBe(3);

    // 8) 用户端：排队条消失、输入解锁（收到 ASSIGNED）
    await expect(userPage.locator('.queue-banner'), '被补位后排队提示应消失').toBeHidden({ timeout: 8000 });
    await expect(userPage.getByPlaceholder('请输入想咨询的问题')).toBeEnabled({ timeout: 8000 });
  } finally {
    await apiPost(request, '/admin/chat/presence', { staffId: STAFF_ID, presence: 'OFFLINE' }, adminToken);
    await resetOpenSessions(request, adminToken);
    await userCtx.close();
    await adminCtx.close();
  }
});
