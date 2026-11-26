import { test, expect, type Page } from '@playwright/test';

/**
 * 业务报表版式（2026-11-21 第三次调整后定稿）。
 *
 * 演进：截图对齐的信息结构原本是「左趋势 / 右环形占比 + 下方排行表」，
 * 但长尾业务下环形图「其他」占 76.7%，用户判定为「多余且难看」，
 * 要求物理删除环形图、把热销 Top 5 挪到右上角、底部换成全宽大客户排行。
 * 2026-11-23 用户再调整：第一行保持「左销售趋势 / 右热销 Top 5」，
 * 但销售趋势由柱状图改为 SVG 手绘折线图（仍不引入 ECharts）。
 *
 * 2026-11-23 第五次调整：大客户 Top 5 从「底部全宽」移到第二行首位，与履约预警、
 * 客户采购结构三卡并排；原「全宽」断言改锁新布局（位于图表行下方、窄于一整行）。
 *
 * 本用例锁六件事：
 * 1) KPI 卡为 5 张且全部有真实值（不得回到 '--'）；
 * 2) 趋势区是折线图：SVG 折线点数 = 统计天数、数据点圆数一致、峰值点唯一，
 *    旧 .trend-bar/.trend-bars 物理清零，保留 Y 轴刻度；
 * 3) 环形图已被物理删除（`.donut*` 一个都不许剩），右栏是热销 Top 5 排行榜；
 * 4) 左趋势与右排行并排且等高（grid stretch，不是靠 margin 凑出来的）；
 * 5) 大客户 Top 5 在第二行，窄于图表整行且位于其下方；
 * 6) 区间切到近 7 天，折线点数与圆点数同步变 7（后端重新聚合）。
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

test('业务报表：五张 KPI 卡 + 折线趋势 + 右侧热销 Top 5 + 底部全宽大客户排行', async ({
  page,
}) => {
  await login(page);
  await page.goto(ADMIN_URL + 'report/sales');
  await page.waitForSelector('.chart-panel--main .trend-line', { timeout: 20000 });
  await page.waitForSelector('.chart-panel--side .hot-rank__item', { timeout: 20000 });
  await page.waitForTimeout(600);

  // ① 五张 KPI 卡，全部是真实值
  await expect(page.locator('.kpi-card')).toHaveCount(5);
  const values = await page.locator('.kpi-card__value').allInnerTexts();
  console.log('KPI:', values.join(' | '));
  expect(values).toHaveLength(5);
  expect(values.every((v) => v.trim() !== '' && v.trim() !== '--')).toBe(true);

  // ② 趋势是折线图：默认近 30 天 = 30 个折线点 + 30 个圆点；峰值点唯一；旧柱状图物理清零
  const line = page.locator('.chart-panel--main .trend-line');
  await expect(line).toBeVisible();
  const pointsAttr = await line.getAttribute('points');
  expect(pointsAttr).toBeTruthy();
  const linePoints = pointsAttr!.trim().split(/\s+/);
  expect(linePoints).toHaveLength(30);
  await expect(page.locator('.chart-panel--main .trend-dot')).toHaveCount(30);
  await expect(page.locator('.chart-panel--main .trend-dot--peak')).toHaveCount(1);
  await expect(page.locator('.trend-bar')).toHaveCount(0);
  await expect(page.locator('.trend-bars')).toHaveCount(0);
  const ticks = await page.locator('.trend-axis__tick').count();
  expect(ticks).toBeGreaterThanOrEqual(3);
  // 折线 y 坐标不全相同（真实数据有波动，不是一条直线）
  const ys = linePoints.map((p) => Number(p.split(',')[1]));
  expect(new Set(ys).size).toBeGreaterThan(1);

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

  // ⑥ 大客户排行（已移到第二行）：有行、有名称与金额，宽度窄于图表整行
  const widePanel = page.locator('.sent-panel.report-top-customer');
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
  // 移位后是第二行三卡之一，明显窄于图表整行
  expect(wideBox!.width).toBeLessThan(rowBox!.width - 100);
  // 大客户卡仍在图表行下方（不是被挤到侧面）
  expect(wideBox!.y).toBeGreaterThan(rowBox!.y + rowBox!.height - 2);

  // ⑦ 区间切到近 7 天：折线点数与圆点数同步变 7（证明趋势也随筛选重新聚合）
  await page.locator('.el-radio-button__inner', { hasText: '近 7 天' }).first().click();
  await expect(page.locator('.chart-panel--main .trend-dot')).toHaveCount(7);
  const points7 = await page.locator('.chart-panel--main .trend-line').getAttribute('points');
  expect(points7!.trim().split(/\s+/)).toHaveLength(7);
});
