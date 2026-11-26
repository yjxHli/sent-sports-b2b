import { test, expect, type Page } from '@playwright/test';
import { ensurePendingShipOrder, ensureWarningSku } from './demo-state';

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

function yuan(text: string): number {
  return Number(text.replace(/[^\d.]/g, '') || '0');
}

test('热销商品 Top 5：真实数据、按成交金额降序、统计区间下推', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'report/sales');
  await page.waitForSelector('.chart-panel--side .hot-rank__item', { timeout: 15000 });
  await page.waitForTimeout(800);

  // ① 右栏是 Top 5 排行（原环形占比卡已删除），行数固定 5
  const hotItems = page.locator('.chart-panel--side .hot-rank__item');
  await expect(hotItems).toHaveCount(5);
  const panelText = await page.locator('.sent-panel').last().innerText();
  expect(panelText).not.toContain('占位');
  expect(panelText).not.toContain('后端 P4');

  // ② 名称是真实商品、金额为正且降序
  const names = await hotItems.locator('.hot-rank__name').allInnerTexts();
  const amounts = (await hotItems.locator('.hot-rank__amount').allInnerTexts()).map(yuan);
  console.log('Top5:', names.map((n, i) => `${i + 1}.${n}=${amounts[i]}`).join(' | '));
  expect(names.every((n) => n.trim().length > 0 && !n.includes('占位'))).toBe(true);
  expect(amounts.every((a) => a > 0)).toBe(true);
  expect(amounts.every((a, i) => i === 0 || a <= amounts[i - 1])).toBe(true);
  // 第一名的进度条必须占满（按第 1 名归一）
  await expect(hotItems.first().locator('.hot-rank__fill--first')).toBeVisible();

  // ③ 切换「近 7 天」必须由后端按新区间重新聚合
  const [rankResp] = await Promise.all([
    page.waitForResponse(
      (r) => r.url().includes('/api/admin/reports/hot-products')
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
  // 热销商品返回的是「区间汇总 + Top N 列表」对象，不是裸数组
  expect(Array.isArray(body.data.items)).toBe(true);
  // 口径说明同步切到 7 天，证明前端确实换了新的统计区间
  // （页面底部还有一张大客户卡，口径文案几乎一样，必须限定在右侧热销面板内）
  await expect(
    page.locator('.chart-panel--side').getByText(/聚合近 7 天已支付订单/),
  ).toBeVisible();
});

test('工作台：六张指标卡全部来自真实报表，不再有 -- 与占位', async ({ page }) => {
  // 「待发货 > 0」「预警 SKU > 0」是绝对数断言，种子跑久了会不成立（单发完了／库存补上了）。
  // 缺失时才临时补一笔待发货单、抬一个 SKU 的安全库存，跑完还原；库是好的时候一行 SQL 都不写。
  const restoreWarning = ensureWarningSku();
  const restoreOrder = ensurePendingShipOrder();
  try {
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
  } finally {
    restoreOrder();
    restoreWarning();
  }
});
