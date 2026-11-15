import { test, expect, type Page } from '@playwright/test';

/**
 * 销售业绩排行 + 工作台指标卡「有数据吗」（TDD 红→绿）。
 *
 * 用户截图追问：/admin/report/sales 底部排行只有「1 占位 ¥0.00 共 1 条」；
 * /admin/dashboard 六张卡除库存预警外是 0 / ¥0.00 / --。
 *
 * 根因：
 * 1) SystemServiceImpl.salesRankReport() / salesReport() 恒返占位 VO；
 * 2) 工作台 cards[2][3][5]（待发货 / 待审核定制 / 待接入会话）前端从未赋值，
 *    一直停在初始 '--'；cards[4] 还把「可用量」误当成「预警 SKU 数」；
 * 3) 角色化待办是静态文案，没有任何数量。
 *
 * 断言口径：排行必须 >1 行、无「占位」、金额为正且按成交金额降序、
 * 统计区间切换要下推到后端重新查询；六张卡必须全部由真实报表接口填值、
 * 不再出现 '--'，待发货 > 0，待办带数量。
 */
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';

async function login(page: Page): Promise<void> {
  await page.goto(ADMIN_URL + 'login');
  await page.waitForTimeout(900);
  if (page.url().includes('login')) {
    await page.getByPlaceholder('用户名').fill('admin');
    await page.getByPlaceholder('密码').fill('123456');
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
      ),
      page.getByRole('button', { name: /登\s*录/ }).click(),
    ]);
  }
}

async function cellTexts(page: Page, colIndex: number): Promise<string[]> {
  return page.$$eval(
    `.el-table__body-wrapper tbody tr td:nth-child(${colIndex})`,
    (tds) => (tds as HTMLElement[]).map((td) => td.innerText.trim()),
  );
}

function yuan(text: string): number {
  return Number(text.replace(/[^\d.]/g, '') || '0');
}

test('销售业绩排行：多行真实数据、按成交金额降序、统计区间下推', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'report/sales');
  await page.waitForSelector('.el-table__body-wrapper tbody tr', { timeout: 15000 });
  await page.waitForTimeout(1000);

  const rows = page.locator('.el-table__body-wrapper tbody tr');
  const rowCount = await rows.count();
  console.log('排行行数:', rowCount);
  // ① 不再是单条占位
  expect(rowCount).toBeGreaterThan(1);
  const panelText = await page.locator('.sent-panel').last().innerText();
  expect(panelText).not.toContain('占位');
  expect(panelText).not.toContain('后端 P4');

  // ② 名称是真实员工、金额为正且降序
  const names = await cellTexts(page, 2);
  const amounts = (await cellTexts(page, 5)).map(yuan);
  const ranks = (await cellTexts(page, 1)).map((t) => Number(t));
  console.log('排行:', names.map((n, i) => `${ranks[i]}.${n}=${amounts[i]}`).join(' | '));
  expect(names.every((n) => n.length > 0 && !n.includes('占位'))).toBe(true);
  expect(amounts.every((a) => a > 0)).toBe(true);
  expect(amounts.every((a, i) => i === 0 || a <= amounts[i - 1])).toBe(true);
  expect(ranks[0]).toBe(1);

  // ③ 切换「近 7 天」必须由后端按新区间重新聚合
  const [rankResp] = await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes('/api/admin/reports/sales-ranking')
        && r.url().includes('days=7')
        && r.request().method() === 'GET',
      { timeout: 15000 },
    ),
    // Element Plus 的原生 input 是透明覆盖层，点它未必触发 label 的 change；点可见文案节点最稳
    page.locator('.el-radio-button__inner', { hasText: '近 7 天' }).first().click(),
  ]);
  expect(rankResp.status()).toBe(200);
  const body = await rankResp.json();
  expect(body.code).toBe(0);
  expect(Array.isArray(body.data)).toBe(true);
  // 口径说明同步切到 7 天，证明前端确实换了新的统计区间
  await expect(page.getByText(/聚合近 7 天已支付订单/)).toBeVisible();
});

test('工作台：六张指标卡全部来自真实报表，不再有 -- 与占位', async ({ page }) => {
  await login(page);
  const [summaryResp] = await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes('/api/admin/reports/dashboard')
        && r.request().method() === 'GET',
      { timeout: 20000 },
    ),
    page.goto(ADMIN_URL + 'dashboard'),
  ]);
  expect(summaryResp.status()).toBe(200);
  await page.waitForTimeout(1200);

  const values = await page.$$eval('.metric-value', (els) =>
    (els as HTMLElement[]).map((el) => el.innerText.trim()),
  );
  const hints = await page.$$eval('.metric-hint', (els) =>
    (els as HTMLElement[]).map((el) => el.innerText.trim()),
  );
  console.log('工作台六卡:', values.join(' | '));
  expect(values).toHaveLength(6);
  // ① 不再有未接接口的 '--'
  expect(values.every((v) => v !== '--')).toBe(true);
  // ② 提示文案不再是「等待 XX 接口」
  expect(hints.some((h) => h.includes('等待'))).toBe(false);
  // ③ 真实待办：待发货 > 0，预警 SKU 数 > 0
  expect(Number(values[2].replace(/[^\d]/g, ''))).toBeGreaterThan(0);
  expect(Number(values[4].replace(/[^\d]/g, ''))).toBeGreaterThan(0);
  // ④ 角色化待办带上真实数量
  const todoText = await page.locator('.todo-list').innerText();
  console.log('待办:', todoText.replace(/\s+/g, ' ').slice(0, 220));
  expect(todoText).toMatch(/\d+\s*项/);
});
