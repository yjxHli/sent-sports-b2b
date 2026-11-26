/**
 * 订单详情「淘宝式」按钮全链路（2026-11-15）。
 *
 * 用户诉求：「订单详情页需要仿照淘宝的详情页，这些按钮都需要实现一下」——
 * 截图里的按钮是：再买一单 / 加入购物车 / 申请开票 / 删除订单 / 打印 / 查看交易快照 /
 * 申请售后（商品行）/ 收起更多订单信息。
 *
 * 断言口径：
 *   1) 已完成订单的操作行给齐「再买一单、加入购物车、申请开票、删除订单、打印」五个按钮，
 *      且「删除订单」只在终态出现（在途订单删了客服就找不到单）；
 *   2) 「查看交易快照」弹出成交时刻的商品行与金额，不是一句占位文案；
 *   3) 「打印」能触发浏览器打印（这里把 window.print 打桩，避免真的弹系统打印框）；
 *   4) 「删除订单」必须二次确认，点取消后订单仍在（真删会打穿后续用例的数据依赖）。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = (process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

const pathOf = (page: Page): string => new URL(page.url()).pathname;

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL}/login`);
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

/** 打开某个状态的订单详情（取列表第一张卡） */
async function openDetailOf(page: Page, status: string): Promise<void> {
  await page.goto(`${USER_URL}/orders?status=${status}`);
  await expect(page.locator('.order-card').first()).toBeVisible({ timeout: 20_000 });
  await page.locator('.order-card').first().getByRole('button', { name: '查看详情' }).click();
  await expect.poll(() => pathOf(page), { timeout: 20_000 }).toMatch(/^\/order\/SO\d+/);
  await expect(page.locator('.od-actions')).toBeVisible({ timeout: 20_000 });
}

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

test('已完成订单：淘宝式按钮齐备，交易快照与打印都能用', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await openDetailOf(page, 'COMPLETED');

  const actions = page.locator('.od-actions');
  for (const name of ['再买一单', '加入购物车', '删除订单', '打印']) {
    await expect(actions.getByRole('button', { name }), `已完成订单应有「${name}」`).toBeVisible();
  }
  await expect(actions.getByRole('button', { name: /申请开票|查看发票/ })).toBeVisible();

  // 右栏「订单信息」要有支付方式与订单编号（淘宝右侧那栏的核心字段）
  const side = page.locator('.od-col-side');
  await expect(side).toContainText('订单编号');
  await expect(side).toContainText('支付方式');

  // 查看交易快照：弹窗里必须是真实商品行，不是占位
  await page.locator('.od-shop__snap').click();
  const snap = page.locator('.el-dialog').filter({ hasText: '交易快照' });
  await expect(snap).toBeVisible({ timeout: 15_000 });
  await expect(snap.locator('.od-snap__table tbody tr').first(), '快照要有商品行').toBeVisible();
  await expect(snap).toContainText('实付');
  // 中文 locale 后弹窗右上角的 X 也叫「关闭此对话框」，用 exact 锁定底部「关闭」按钮
  await snap.getByRole('button', { name: '关闭', exact: true }).click();
  await expect(snap).toBeHidden({ timeout: 10_000 });

  // 打印：打桩 window.print，只验证按钮真的调到了它
  await page.evaluate(() => {
    (window as any).__printed = 0;
    window.print = () => { (window as any).__printed += 1; };
  });
  await actions.getByRole('button', { name: '打印' }).click();
  await expect
    .poll(() => page.evaluate(() => (window as any).__printed as number))
    .toBeGreaterThan(0);

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

test('在途订单：不给「删除订单 / 再买一单」，避免把还在路上的单删掉', async ({ page }) => {
  await loginUser(page);
  await openDetailOf(page, 'WAIT_SHIP');

  const actions = page.locator('.od-actions');
  await expect(
    actions.getByRole('button', { name: '删除订单' }),
    '在途订单不应出现删除入口',
  ).toHaveCount(0);
  await expect(actions.getByRole('button', { name: '再买一单' })).toHaveCount(0);
  // 在途单该有的操作还在
  await expect(actions.getByRole('button', { name: /申请退款|联系客服/ }).first()).toBeVisible();
});

test('删除订单必须二次确认，点取消后订单仍在', async ({ page }) => {
  await loginUser(page);
  await openDetailOf(page, 'COMPLETED');
  const before = pathOf(page);

  await page.locator('.od-actions').getByRole('button', { name: '删除订单' }).click();

  const box = page.locator('.el-message-box');
  await expect(box, '删除订单要弹二次确认').toBeVisible({ timeout: 15_000 });
  await expect(box).toContainText('不再显示在「我的订单」中');

  await box.getByRole('button', { name: '取消' }).click();
  await expect(box).toBeHidden({ timeout: 10_000 });

  // 取消后什么都没发生：还在详情页，订单行还在
  expect(pathOf(page)).toBe(before);
  await expect(page.locator('.od-table .od-goods').first()).toBeVisible();
});

/**
 * 「加入购物车」必须真的打到 /api/cart/items，而不是只弹一句提示。
 * 商品可能已下架或库存不足（种子 SKU 会被历次用例跑到 0），因此这里只锁「确实发起了加购请求」，
 * 成功后把本次新增的购物车行删掉，避免污染其它用例的购物车/结算断言。
 */
test('加入购物车真的发起加购请求（用完清理）', async ({ page }) => {
  await loginUser(page);
  const api = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088').replace(/\/+$/, '');
  const token = await page.evaluate(() => window.localStorage.getItem('sent_user_access_token') ?? '');
  expect(token, '登录后应拿到用户端令牌').not.toBe('');
  const headers = { Authorization: `Bearer ${token}` };

  const before = await (await page.request.get(`${api}/api/cart`, { headers })).json().catch(() => null);
  const beforeIds = new Set<string>((before?.data ?? []).map((i: any) => String(i.id)));

  await openDetailOf(page, 'COMPLETED');

  const posted = page.waitForResponse(
    (r) => r.url().includes('/api/cart/items') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.locator('.od-actions').getByRole('button', { name: '加入购物车' }).click();
  const resp = await posted;
  expect([200, 400], `加购请求异常状态：${resp.status()}`).toContain(resp.status());

  if (resp.ok()) {
    await expect(page.locator('.el-message').filter({ hasText: '已加入购物车' })).toBeVisible({
      timeout: 15_000,
    });
    // 清理本次新增的行（保留用例前就存在的购物车内容）
    const after = await (await page.request.get(`${api}/api/cart`, { headers })).json().catch(() => null);
    for (const it of after?.data ?? []) {
      if (!beforeIds.has(String(it.id))) {
        await page.request.delete(`${api}/api/cart/items/${it.id}`, { headers });
      }
    }
  }
});
