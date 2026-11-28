/**
 * 订单服务「退款/售后」落点 + 我的售后页版式（2026-11-16，TDD 红→绿）。
 *
 * 用户诉求（第 4 轮）：
 *   6) 点订单服务里的「退款/售后」，应该进**这一单专属**的申请页，而不是整个退款售后列表；
 *   7) 「我的售后」页 UI 重做（原来是「单号 + 状态 + 金额」三行字，看不出退的是哪件商品、卡在哪一步）。
 *
 * 断言口径：
 *   - 订单详情的「退款 / 售后」跳 `/refund/apply/{orderNo}`，且申请页列出该订单的商品行；
 *   - 申请页不再是「我的售后」列表（URL 与页面根节点都不同）；
 *   - 我的售后列表：卡片带商品名 / 件数 / 退款金额 / 四步进度，状态筛选可切换。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const API_URL = process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api';

const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

const pathOf = (page: Page): string => new URL(page.url()).pathname;

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/login`);
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

async function userToken(page: Page): Promise<string> {
  return page.evaluate(
    () => Object.entries(localStorage).find(([k]) => k.includes('access_token'))?.[1] ?? '',
  );
}

/** 取当前客户任意一笔订单号（详情页「退款/售后」入口对所有状态都在） */
async function anyOrderNo(page: Page): Promise<string> {
  const token = await userToken(page);
  const res = await page.request.get(`${API_URL.replace(/\/+$/, '')}/orders?page=1&size=1`, {
    headers: { Authorization: `Bearer ${token}` },
    failOnStatusCode: false,
  });
  const body: any = await res.json().catch(() => null);
  const orderNo = body?.data?.list?.[0]?.orderNo;
  expect(orderNo, `演示客户应至少有一笔订单：${JSON.stringify(body)}`).toBeTruthy();
  return String(orderNo);
}

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

test('订单服务「退款/售后」进入该订单专属申请页，而不是售后列表', async ({ page }) => {
  test.setTimeout(120_000);
  const consoleErrors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) consoleErrors.push(m.text());
  });

  await loginUser(page);
  const orderNo = await anyOrderNo(page);

  await page.goto(`${USER_URL.replace(/\/+$/, '')}/order/${orderNo}`);
  const servBtn = page.locator('.od-serv').filter({ hasText: '退款 / 售后' });
  await expect(servBtn, '右栏订单服务应有「退款 / 售后」入口').toBeVisible({ timeout: 20_000 });

  await servBtn.click();
  await expect.poll(() => pathOf(page), { timeout: 20_000 }).toBe(`/refund/apply/${orderNo}`);
  await expect(page.locator('.ra-page'), '应进入按订单的申请页').toBeVisible({ timeout: 20_000 });
  await expect(
    page.locator('.ra-line').first(),
    '申请页应列出该订单的商品行（可勾选要退的件）',
  ).toBeVisible({ timeout: 20_000 });
  await expect(page.locator('.ra-submit'), '申请页应有底部提交条').toBeVisible();

  expect(consoleErrors, `console errors: ${consoleErrors.join(' | ')}`).toHaveLength(0);
});

test('我的售后：卡片带商品与进度，状态筛选可用', async ({ page }) => {
  test.setTimeout(120_000);
  const consoleErrors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) consoleErrors.push(m.text());
  });

  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/refunds`);
  await expect(page.locator('.rf-page'), '我的售后页应渲染').toBeVisible({ timeout: 20_000 });

  const cards = page.locator('.rf-card');
  await expect(cards.first(), '演示客户应有多笔售后单').toBeVisible({ timeout: 20_000 });

  const first = cards.first();
  // 商品名来自 order_item 快照；件数与金额一并展示
  await expect(first.locator('.rf-card__name'), '卡片应显示商品名').not.toBeEmpty();
  await expect(first.locator('.rf-card__meta'), '卡片应显示订单号与件数').toContainText('共');
  await expect(first.locator('.rf-card__money b'), '卡片应显示退款金额').toContainText('¥');
  await expect(first.locator('.rf-step'), '卡片应有四步进度').toHaveCount(4);
  await expect(first.getByRole('button', { name: '查看进度' }), '应能查看退款进度').toBeVisible();

  // 状态筛选：点「待审核」后 URL 不变、列表按状态过滤（可能为空，空态文案要正确）
  await page.locator('.rf-tab', { hasText: '待审核' }).click();
  await expect(page.locator('.rf-tab--on'), '筛选态应高亮').toContainText('待审核');
  await expect(
    page.locator('.rf-card, .rf-empty'),
    '筛选后应要么有卡片，要么给出空态',
  ).not.toHaveCount(0);

  expect(consoleErrors, `console errors: ${consoleErrors.join(' | ')}`).toHaveLength(0);
});
