import { test, expect, type Page } from '@playwright/test';

/**
 * 业务报表版式（2026-11-21 第三次调整后定稿）。
 *
 * 演进：截图对齐的信息结构原本是「左趋势 / 右环形占比 + 下方排行表」，
 * 但长尾业务下环形图「其他」占 76.7%，用户判定为「多余且难看」，
 * 要求物理删除环形图、把热销 Top 5 挪到右上角、底部换成全宽大客户排行。
 *
 * 本用例锁五件事：
 * 1) KPI 卡为 5 张且全部有真实值（不得回到 '--'）；
 * 2) 趋势区仍是柱状图（用户明确要求），柱子数 = 统计天数，有 Y 轴刻度；
 * 3) 环形图已被物理删除（`.donut*` 一个都不许剩），右栏是热销 Top 5 排行榜；
 * 4) 左趋势与右排行并排且等高（grid stretch，不是靠 margin 凑出来的）；
 * 5) 底部是全宽的大客户 Top 5 卡片，宽度与整行等宽。
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

test('业务报表：五张 KPI 卡 + 柱状趋势 + 右侧热销 Top 5 + 底部全宽大客户排行', async ({
  page,
}) => {
  await login(page);
  await page.goto(ADMIN_URL + 'report/sales');
  await page.waitForSelector('.trend-bar', { timeout: 20000 });
  await page.waitForSelector('.chart-panel--side .hot-rank__item', { timeout: 20000 });
  await page.waitForTimeout(600);

  // ① 五张 KPI 卡，全部是真实值
  await expect(page.locator('.kpi-card')).toHaveCount(5);
  const values = await page.locator('.kpi-card__value').allInnerTexts();
  console.log('KPI:', values.join(' | '));
  expect(values).toHaveLength(5);
  expect(values.every((v) => v.trim() !== '' && v.trim() !== '--')).toBe(true);

  // ② 趋势仍是柱状图：默认近 30 天 = 30 根柱；有 Y 轴刻度
  const bars = await page.locator('.trend-bar').count();
  expect(bars).toBe(30);
  const ticks = await page.locator('.trend-axis__tick').count();
  expect(ticks).toBeGreaterThanOrEqual(3);
  // 柱子高度不是全部 0（真实数据）
  const heights = await page.$$eval('.trend-bar', (els) =>
    (els as HTMLElement[]).map((el) => el.getBoundingClientRect().height),
  );
  expect(heights.some((h) => h > 1)).toBe(true);

  // ③ 环形占比图已被物理删除：模板、样式、DOM 全清零，不允许以任何形式复活
  await expect(page.locator('.donut')).toHaveCount(0);
  await expect(page.locator('.donut__sector')).toHaveCount(0);
  await expect(page.locator('.donut__pct')).toHaveCount(0);

  // ④ 右栏是热销商品 Top 5 排行榜：5 行、名称非空、金额降序、第 1 名橙条
  const hotItems = page.locator('.chart-panel--side .hot-rank__item');
  await expect(hotItems).toHaveCount(5);
  const hotNames = await hotItems.locator('.hot-rank__name').allInnerTexts();
  const hotAmounts = (await hotItems.locator('.hot-rank__amount').allInnerTexts()).map((t) =>
    Number(t.replace(/[^\d.]/g, '') || '0'),
  );
  console.log(
    '热销 Top5:',
    hotNames.map((n, i) => `${i + 1}.${n}=${hotAmounts[i]}`).join(' | '),
  );
  expect(hotNames.every((n) => n.trim().length > 0)).toBe(true);
  expect(hotAmounts.every((a) => a > 0)).toBe(true);
  expect(hotAmounts.every((a, i) => i === 0 || a <= hotAmounts[i - 1])).toBe(true);
  await expect(hotItems.first().locator('.hot-rank__fill--first')).toBeVisible();

  // ⑤ 左趋势与右排行并排且等高（grid align-items: stretch）
  const mainBox = await page.locator('.chart-panel--main').boundingBox();
  const sideBox = await page.locator('.chart-panel--side').boundingBox();
  expect(mainBox && sideBox).toBeTruthy();
  expect(Math.abs((mainBox!.y ?? 0) - (sideBox!.y ?? 0))).toBeLessThanOrEqual(2);
  expect(Math.abs((mainBox!.height ?? 0) - (sideBox!.height ?? 0))).toBeLessThanOrEqual(2);

  // ⑥ 底部全宽大客户排行：有行、有名称与金额，宽度与图表整行等宽
  const widePanel = page.locator('.sent-panel', { has: page.locator('.hot-rank--wide') });
  await expect(widePanel).toHaveCount(1);
  const customerItems = widePanel.locator('.hot-rank--wide .hot-rank__item');
  const customerCount = await customerItems.count();
  expect(customerCount).toBeGreaterThan(0);
  expect(customerCount).toBeLessThanOrEqual(5);
  const customerNames = await customerItems.locator('.hot-rank__name').allInnerTexts();
  console.log('大客户 Top5:', customerNames.join(' | '));
  expect(customerNames.every((n) => n.trim().length > 0)).toBe(true);
  // 手机号脱敏：不许出现完整 11 位手机号
  const wideText = await widePanel.innerText();
  expect(wideText).not.toMatch(/1[3-9]\d{9}/);

  // 强调色只给两张榜的第 1 名（共 2 处），其余名次不许也挂第一名样式（否则强调失效）
  await expect(page.locator('.hot-rank__fill--first')).toHaveCount(2);

  const rowBox = await page.locator('.chart-row').boundingBox();
  const wideBox = await widePanel.boundingBox();
  expect(rowBox && wideBox).toBeTruthy();
  expect(Math.abs((rowBox!.width ?? 0) - (wideBox!.width ?? 0))).toBeLessThanOrEqual(2);
  // 大客户卡在图表行下方（不是被挤到侧面）
  expect(wideBox!.y).toBeGreaterThan(rowBox!.y + rowBox!.height - 2);
});
