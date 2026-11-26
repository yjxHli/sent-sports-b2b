/**
 * 管理端「话术配置」（V60）：知识库 CRUD + 热更新 + 权限隔离。
 *
 * 断言链（真实读写，不 mock）：
 *   1) admin 登录 → 客服 → 话术配置：新增一条「退换货 → 支持7天无理由」；
 *   2) 用户端客服窗口发「退换货」，机器人<b>立刻</b>回这句新话术，且用户端全程不刷新；
 *   3) 管理端把回复改掉，用户端再发同一句话立刻拿到新回复（改也秒生效，不用重启）；
 *   4) 命中次数写回管理端列表（运营据此判断话术有没有用）；
 *   5) 权限隔离：持 '*' 的销售账号看不到菜单、直连落 403、调接口 40301。
 *
 * 另有独立用例覆盖「删除」（2026-11-25）：进入删除态 → 勾选 → 输入「删除」换令牌 → 软删，
 * 删完列表与接口都查不到，但库里那一行还在（deleted=1），命中次数不丢。 *
 * 两个必须写清楚的关键细节：
 *
 * 1) **关键词要能赢过种子话术**。种子库里已有一条「退货,退款,退换,换货」，
 *    按现有评分（命中词数 × 100 + 最长命中词长），客户说「退换货」时它会命中
 *    「退换」+「换货」两个词拿 202 分，压倒只命中一个 3 字词（103 分）的新话术。
 *    所以这里用更具体的「无理由退货」（1 个词命中、5 字 = 105 分，压过种子的「退货」102 分）。
 *    这也顺带验证了「更长的词更具体」这条排序规则。
 * 2) **断言必须精确比对整句**。种子话术的回复同样含「支持7天无理由」，
 *    用 `toContainText` 会假绿——必须 `toHaveText` 比对新话术全文。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import {
  ADMIN_NAME, ADMIN_PASSWORD, API_URL, CUSTOMER_ID, CUSTOMER_PASSWORD, CUSTOMER_PHONE, USER_URL,
  apiCall, clearOpenSessions, join, loginApi, closeSessionWithTicket,
} from './chat-helpers';

const ADMIN_URL = (process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/').replace(/\/+$/, '');
const adminPath = (path: string): string => `${ADMIN_URL}/${path.replace(/^\/+/, '')}`;
const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';

const KEYWORDS = '无理由退货';
const REPLY = '支持7天无理由';
const REPLY_UPDATED = '支持7天无理由（话术已更新）';
/** 删除用例专用的关键词与回复：不与机器人排序、种子话术互相干扰 */
const DEL_KEYWORDS = '软删回归话术';
const DEL_REPLY = '这条话术只用于验证删除链路，稍后会被软删';

function mysqlBin(): string {
  if (process.env.E2E_MYSQL_BIN) return process.env.E2E_MYSQL_BIN;
  return existsSync(LOCAL_MYSQL) ? LOCAL_MYSQL : 'mysql';
}

function sql(statement: string): string {
  return execFileSync(mysqlBin(), [
    '-h127.0.0.1', '-P3306', '-uroot', `-p${process.env.E2E_DB_PASSWORD ?? '123456'}`,
    'sent_dev', '-N', '-B', '-e', statement,
  ], { encoding: 'utf8' }).trim();
}

/**
 * 清掉本用例造的话术（关键词是这两条用例独有的，按它精确删）。
 *
 * 顺序有讲究：**先走接口停用、再物理删除**。
 * 直接在库里 DELETE 不会让后端知识库快照失效，机器人可能继续拿旧快照回复；
 * 先停用会触发服务端刷新（停用后的条目不再进候选集），此后删除与缓存状态一致。
 *
 * 注：管理端「删除」是软删（deleted=1），残留行会被列表与候选集自动过滤；
 * 这里清场仍走物理 DELETE，避免反复跑用例把表撑成垃圾场。
 */
async function cleanupFaq(
  request: APIRequestContext,
  adminToken: string,
): Promise<void> {
  for (const keywords of [KEYWORDS, DEL_KEYWORDS]) {
    try {
      const listed = await apiCall(request, 'GET', `/admin/faq?page=1&size=50&keyword=${keywords}`,
        undefined, adminToken);
      for (const row of listed?.data?.list ?? []) {
        if (row.keywords !== keywords) continue;
        await apiCall(request, 'PUT', `/admin/faq/${Number(row.id)}/status`,
          { status: 'DISABLED' }, adminToken);
      }
    } catch {
      // 接口不可用时不阻塞清理，直接落库删
    }
    try {
      sql(`DELETE FROM faq_knowledge WHERE keywords = '${keywords}'`);
    } catch {
      // 清理失败不掩盖用例本身的断言结果
    }
  }
}

async function loginAdmin(page: Page): Promise<void> {
  await page.goto(adminPath('login'));
  await expect(page.getByPlaceholder('用户名')).toBeVisible({ timeout: 15_000 });
  await page.getByPlaceholder('用户名').fill('admin');
  await page.getByPlaceholder('密码').fill('123456');
  await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST'),
    page.getByRole('button', { name: /登\s*录/ }).click(),
  ]);
  await expect(page, 'admin 未登录成功').not.toHaveURL(/\/login/, { timeout: 20_000 });
}

async function loginCustomer(page: Page): Promise<void> {
  await page.goto(join(USER_URL, '/login'));
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

/** 用户端发一句话（客服窗口已在 /chat） */
async function askBot(page: Page, text: string): Promise<void> {
  const box = page.getByPlaceholder('请输入想咨询的问题');
  await expect(box).toBeEnabled({ timeout: 20_000 });
  await box.fill(text);
  await box.press('Enter');
}

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

test('话术配置：新增 → 用户端秒回 → 修改 → 立刻用新话术', async ({ browser, request }) => {
  test.setTimeout(180_000);
  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
  const userToken = await loginApi(request, CUSTOMER_PHONE, CUSTOMER_PASSWORD, 'user');
  await cleanupFaq(request, adminToken);
  await clearOpenSessions(request, adminToken);

  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const adminPage = await ctx.newPage();
  const userPage = await ctx.newPage();

  try {
    // ---------- 1) 管理端新增话术 ----------
    await loginAdmin(adminPage);
    // 菜单里能直接看到「话术配置」（不是只能靠 URL 猜）
    await expect(adminPage.locator('.sent-menu'), '客服分组应有「话术配置」菜单')
      .toContainText('话术配置');
    await adminPage.goto(adminPath('chat/faq'));
    await expect(adminPage.locator('h2.sent-page-title')).toHaveText('话术配置', { timeout: 20_000 });

    await adminPage.getByTestId('faq-create-btn').click();
    const createDialog = adminPage.locator('.el-dialog').filter({ hasText: '新增话术' });
    await expect(createDialog, '点「新增话术」应弹出表单').toBeVisible({ timeout: 10_000 });
    // el-input 会把 data-testid 透传到内部原生 input / textarea 上，直接对 testid 本身操作
    await adminPage.getByTestId('faq-keyword-input').fill(KEYWORDS);
    await adminPage.getByTestId('faq-reply-input').fill(REPLY);
    await Promise.all([
      adminPage.waitForResponse((r) => /\/api\/admin\/faq$/.test(r.url()) && r.request().method() === 'POST'),
      adminPage.getByTestId('faq-save-btn').click(),
    ]);
    await expect(createDialog).toBeHidden({ timeout: 10_000 });

    const row = adminPage.locator('[data-testid="faq-list"] .el-table__body-wrapper tbody tr')
      .filter({ hasText: KEYWORDS });
    await expect(row, '新增后列表应出现这条话术').toHaveCount(1, { timeout: 20_000 });
    await expect(row).toContainText(REPLY);
    await expect(row).toContainText('启用中');

    // ---------- 2) 用户端发「退换货」：机器人立刻用新话术（此后用户端不再刷新） ----------
    await loginCustomer(userPage);
    await userPage.goto(join(USER_URL, '/chat'));
    await expect(userPage.getByTestId('bot-welcome'), '客户进线应看到机器人迎宾').toBeVisible({ timeout: 20_000 });

    await askBot(userPage, KEYWORDS);
    // 精确比对：种子话术的回复也含「支持7天无理由」，用 toContainText 会假绿。
    // 机器人回复自 2026-11-24 起按正常会话气泡渲染（senderType=BOT），不再是 .chat-system 灰字；
    // 断言要落到气泡上——整行还带头像「🤖」，对整行取 toHaveText 会多出两个字
    const botBubble = userPage.getByTestId('chat-bot-msg').last().locator('.chat-bubble');
    await expect(botBubble, '机器人应立刻回新配的话术').toHaveText(REPLY, { timeout: 20_000 });

    // 命中次数回写到管理端：证明这次回复真的走了新条目，而不是文本巧合
    const listed = await apiCall(request, 'GET', `/admin/faq?page=1&size=50&keyword=${KEYWORDS}`,
      undefined, adminToken);
    const created = (listed?.data?.list ?? []).find((x: any) => x.keywords === KEYWORDS);
    expect(created, `管理端应能查到刚新增的话术：${JSON.stringify(listed)}`).toBeTruthy();
    expect(Number(created.hitCount), '用户端命中后命中次数应 ≥ 1').toBeGreaterThanOrEqual(1);
    const faqId = Number(created.id);

    // ---------- 3) 管理端改回复 → 用户端不刷新也立刻用新话术 ----------
    await adminPage.reload();
    const editRow = adminPage.locator('[data-testid="faq-list"] .el-table__body-wrapper tbody tr')
      .filter({ hasText: KEYWORDS });
    await expect(editRow).toHaveCount(1, { timeout: 20_000 });
    await editRow.first().getByTestId('faq-edit-btn').click();
    const editDialog = adminPage.locator('.el-dialog').filter({ hasText: '编辑话术' });
    await expect(editDialog, '点「编辑」应回填原值').toBeVisible({ timeout: 10_000 });
    await expect(adminPage.getByTestId('faq-keyword-input')).toHaveValue(KEYWORDS);
    await adminPage.getByTestId('faq-reply-input').fill(REPLY_UPDATED);
    await Promise.all([
      adminPage.waitForResponse((r) => r.url().endsWith(`/api/admin/faq/${faqId}`)
        && r.request().method() === 'PUT'),
      adminPage.getByTestId('faq-save-btn').click(),
    ]);
    await expect(editDialog).toBeHidden({ timeout: 10_000 });

    await askBot(userPage, KEYWORDS);
    await expect(userPage.getByTestId('chat-bot-msg').last().locator('.chat-bubble'),
      '改完话术没有重启后端，用户端也没刷新——下一条消息就该用新回复')
      .toHaveText(REPLY_UPDATED, { timeout: 20_000 });
  } finally {
    const current = await apiCall(request, 'GET', `/chat/sessions/current?customerId=${CUSTOMER_ID}`,
      undefined, userToken);
    const sessionId = Number(current?.data?.sessionId ?? 0);
    if (sessionId > 0) {
      await closeSessionWithTicket(request, Number(sessionId), adminToken);
    }
    await cleanupFaq(request, adminToken);
    await ctx.close();
  }
});

test('话术删除：进入删除态 → 勾选 → 输入「删除」→ 软删且机器人不再命中', async ({ page, request }) => {
  test.setTimeout(180_000);
  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
  await cleanupFaq(request, adminToken);

  // 前置：先把待删话术建好（UI 新增已被上一条用例覆盖，这里只验删除链路）
  const created = await apiCall(request, 'POST', '/admin/faq',
    { keywords: DEL_KEYWORDS, replyContent: DEL_REPLY, status: 'ENABLED' }, adminToken);
  expect(created?.code, `前置新增话术失败：${JSON.stringify(created)}`).toBe(0);
  const faqId = Number(created?.data?.id);

  try {
    await loginAdmin(page);
    await page.goto(adminPath('chat/faq'));
    await expect(page.locator('h2.sent-page-title')).toHaveText('话术配置', { timeout: 20_000 });

    const row = page.locator('[data-testid="faq-list"] .el-table__body-wrapper tbody tr')
      .filter({ hasText: DEL_KEYWORDS });
    await expect(row, '待删话术应出现在列表里').toHaveCount(1, { timeout: 20_000 });

    // 常态不显示勾选列（首位就是关键词），点「删除」才进入删除态
    await expect(page.locator('[data-testid="faq-list"] .el-table__body-wrapper tbody tr td .el-checkbox'))
      .toHaveCount(0, { timeout: 5_000 });
    await page.getByTestId('batch-delete-btn').click();
    await expect(page.getByTestId('batch-delete-tip'), '进入删除态应显示已选条数').toBeVisible({ timeout: 5_000 });

    await row.locator('.el-checkbox').first().click();
    await expect(page.getByTestId('batch-delete-tip')).toContainText('已选 1 条', { timeout: 5_000 });

    await page.getByTestId('batch-delete-confirm').click();
    // 危险操作确认弹窗：必须精确输入「删除」二字才换得到令牌
    const box = page.locator('.el-message-box');
    await expect(box, '批量删除必须二次确认').toBeVisible({ timeout: 8_000 });
    await box.locator('input').fill('删除');
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/admin/faq/batch-delete') && r.request().method() === 'POST',
        { timeout: 15_000 },
      ),
      box.getByRole('button', { name: '确认继续' }).click(),
    ]);

    await expect(row, '删除后列表不应再有这条话术').toHaveCount(0, { timeout: 20_000 });

    // 接口与库双确认：列表消失 ≠ 真的不进候选集
    const listed = await apiCall(request, 'GET', `/admin/faq?page=1&size=50&keyword=${DEL_KEYWORDS}`,
      undefined, adminToken);
    expect(Number(listed?.data?.total ?? -1), '接口也查不到了').toBe(0);
    expect(sql(`SELECT deleted FROM faq_knowledge WHERE id = ${faqId}`),
      '软删：行还在库里，只是打了 deleted 标记').toBe('1');
  } finally {
    await cleanupFaq(request, adminToken);
  }
});

test('权限隔离：持 * 的销售账号看不到话术配置，接口直接 40301', async ({ page, request }) => {
  test.setTimeout(120_000);
  await page.goto(adminPath('login'));
  await page.getByPlaceholder('用户名').fill('sales');
  await page.getByPlaceholder('密码').fill('123456');
  await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST'),
    page.getByRole('button', { name: /登\s*录/ }).click(),
  ]);
  await expect(page.locator('.sent-menu'), '销售账号不应看到「话术配置」菜单')
    .not.toContainText('话术配置');

  await page.goto(adminPath('chat/faq'));
  await expect(page, '销售账号直连话术配置应落到无权限页')
    .toHaveURL(/\/error\/403/, { timeout: 15_000 });

  // 后端二次校验：话术直接影响客户看到的回复，不能只靠前端藏菜单
  const loginRes = await request.post(`${API_URL}/auth/login`, {
    data: { username: 'sales', password: '123456', aud: 'admin', deviceId: 'e2e-faq-perm' },
  });
  const token = (await loginRes.json())?.data?.accessToken;
  expect(token, '销售账号应能正常登录（否则本用例失去意义）').toBeTruthy();
  const res = await request.get(`${API_URL}/admin/faq?page=1&size=10`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await res.json();
  expect(body.code, `话术配置接口必须拒绝销售账号，实际返回：${JSON.stringify(body)}`).toBe(40301);
});
