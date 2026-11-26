import { test, expect, type Page } from '@playwright/test';

/**
 * 业务报表拓展（2026-11-23，TDD 红→绿）：
 * 1) 七宫格第一行保持「左折线趋势 / 右热销 Top 5」；
 * 2) 大客户 Top 5 从底部全宽移到第二行首位，与「履约预警 / 客户结构」同排；
 * 3) 新增四块：履约风险预警、客户采购结构、商品销售明细、复购/新老客结构；
 * 4) 鼠标移到折线图上出现 tooltip，显示对应日期的销售额与订单数。
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

test('拓展业务报表：四块新卡片 + 大客户移位 + 折线图 hover 显示销售额', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'report/sales');
  await page.waitForSelector('.chart-panel--main .trend-line', { timeout: 20000 });
  await page.waitForTimeout(800);

  // ① 第一行仍是趋势 + 热销 Top 5（原有结构不许被拓展挤掉）
  await expect(page.locator('.chart-panel--main .trend-line')).toBeVisible();
  await expect(page.locator('.chart-panel--side .hot-rank__item')).toHaveCount(5);

  // ② 四块新卡片全部渲染，都有真实数据（不是空壳）
  await expect(page.locator('.report-risk')).toBeVisible();
  const riskRows = await page.locator('.report-risk__item').count();
  expect(riskRows).toBeGreaterThanOrEqual(4);
  // 每行有进度条与严重度徽标，把卡片纵向撑满（不再是 34px 紧凑行）
  await expect(page.locator('.report-risk__bar')).toHaveCount(riskRows);
  await expect(page.locator('.report-risk__level')).toHaveCount(riskRows);
  const riskItemBox = await page.locator('.report-risk__item').first().boundingBox();
  expect(riskItemBox!.height).toBeGreaterThanOrEqual(44);

  await expect(page.locator('.report-customer-mix')).toBeVisible();
  await expect(page.locator('.report-customer-mix__item')).toHaveCount(3);
  // 图例补客户数/订单数，环形放大到 150
  await expect(page.locator('.report-customer-mix__meta')).toHaveCount(3);
  const donutBox = await page.locator('.mix-donut').boundingBox();
  expect(donutBox!.width).toBeGreaterThanOrEqual(140);

  await expect(page.locator('.report-product-detail')).toBeVisible();
  const productRows = await page.locator('.report-product-detail tbody tr').count();
  expect(productRows).toBeGreaterThan(0);

  await expect(page.locator('.report-repeat')).toBeVisible();
  const repeatText = await page.locator('.report-repeat').innerText();
  expect(repeatText).toMatch(/复购率/);
  expect(repeatText).not.toContain('--');
  // 复购频次分三档，横向条撑满下半部
  await expect(page.locator('.report-repeat__bucket')).toHaveCount(3);
  const bucketLabels = await page.locator('.report-repeat__bucket-label').allInnerTexts();
  expect(bucketLabels).toEqual(['2 单', '3–5 单', '6 单以上']);

  // ③ 大客户移位：与「客户结构 / 履约预警」同一行（y 差在 4px 内），不再全宽独占底部
  const customerPanel = page.locator('.report-top-customer');
  await expect(customerPanel).toBeVisible();
  const [customerBox, riskBox, mixBox] = await Promise.all([
    customerPanel.boundingBox(),
    page.locator('.report-risk').boundingBox(),
    page.locator('.report-customer-mix').boundingBox(),
  ]);
  expect(customerBox && riskBox && mixBox).toBeTruthy();
  expect(Math.abs(customerBox!.y - riskBox!.y)).toBeLessThanOrEqual(4);
  expect(Math.abs(customerBox!.y - mixBox!.y)).toBeLessThanOrEqual(4);
  const rowBox = await page.locator('.chart-row').boundingBox();
  expect(customerBox!.y).toBeGreaterThan(rowBox!.y + rowBox!.height - 4);

  // ④ 折线图 hover：出现自定义 tooltip，含日期、金额（¥）与订单数
  const canvas = page.locator('.chart-panel--main .trend-canvas');
  const box = await canvas.boundingBox();
  expect(box).toBeTruthy();
  await page.mouse.move(box!.x + box!.width * 0.5, box!.y + box!.height * 0.5);
  const tooltip = page.locator('.trend-tooltip');
  await expect(tooltip).toBeVisible({ timeout: 5000 });
  const tipText = await tooltip.innerText();
  console.log('折线 tooltip:', tipText.replace(/\s+/g, ' '));
  expect(tipText).toMatch(/\d{4}-\d{2}-\d{2}/);
  expect(tipText).toMatch(/¥[\d,.]+/);
  expect(tipText).toMatch(/订单/);

  // 离开画布后 tooltip 消失
  await page.mouse.move(box!.x - 40, box!.y - 40);
  await expect(tooltip).toHaveCount(0);
});
