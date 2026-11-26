import { test, expect, type Page } from '@playwright/test';

/**
 * 业务报表紧凑化（2026-11-23）：
 * 页面本身信息够，但「同一份空白被算两遍」+「三处 flex 分布把内容摊开」导致观感松散。
 * 本 spec 用实测尺寸锁住四个口径，防止以后加内容时又松回去：
 * 1) 行距只由行容器承担（卡片自身 margin-bottom 归零），相邻行卡间距 ≤ 20px；
 * 2) 复购频次三档均分卡片高度，档间不再出现 100px+ 空档；
 * 3) 客户结构环图紧跟标题（顶部对齐），不再上下各留 40px；
 * 4) 口径说明与商品明细行高收紧，卡片不再被小字与行高撑高。
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

test('业务报表紧凑化：行距 / 卡内留白 / 复购分档 / 表格行高全部收拢', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'report/sales');
  await page.waitForSelector('.chart-panel--main .trend-line', { timeout: 20000 });
  await page.waitForTimeout(900);

  // ① 卡片自身不再带 margin-bottom，行距由 .chart-row / .report-row 唯一承担
  const cardMarginBottom = await page
    .locator('.report-risk')
    .evaluate((el) => parseFloat(getComputedStyle(el).marginBottom));
  expect(cardMarginBottom).toBe(0);

  const row1 = await page.locator('.chart-row').boundingBox();
  const row2 = await page.locator('.report-row--three').boundingBox();
  const row3 = await page.locator('.report-row--two').boundingBox();
  expect(row1 && row2 && row3).toBeTruthy();
  const gap1to2 = Math.round(row2!.y - (row1!.y + row1!.height));
  const gap2to3 = Math.round(row3!.y - (row2!.y + row2!.height));
  expect(gap1to2).toBeLessThanOrEqual(20);
  expect(gap2to3).toBeLessThanOrEqual(20);
  // grid 行高不再被卡片 margin 撑大
  const cardH = (await page.locator('.report-risk').boundingBox())!.height;
  expect(Math.abs(row2!.height - cardH)).toBeLessThanOrEqual(2);

  // ② 卡片内边距 20 → 16，标题区不再叠两层 16px
  const riskPadding = await page.locator('.report-risk').evaluate((el) => {
    const cs = getComputedStyle(el);
    const head = el.querySelector('.sent-panel__head') as HTMLElement;
    const hs = getComputedStyle(head);
    return {
      padTop: parseFloat(cs.paddingTop),
      headGap: parseFloat(hs.marginBottom) + parseFloat(hs.paddingBottom),
    };
  });
  expect(riskPadding.padTop).toBeLessThanOrEqual(16);
  expect(riskPadding.headGap).toBeLessThanOrEqual(24);

  // ③ 复购频次分布改为三列竖柱：三列同高且吃满容器，柱高随人数递增，
  //    不再出现「17px 细行 + 136px 空档」的离散空白
  const bucketBoxes = await page
    .locator('.report-repeat__bucket')
    .evaluateAll((els) => els.map((e) => {
      const b = e.getBoundingClientRect();
      return { x: Math.round(b.x), y: Math.round(b.y), w: Math.round(b.width), h: Math.round(b.height) };
    }));
  expect(bucketBoxes.length).toBe(3);
  // 三列横排（同一个 y），不是竖向堆叠
  expect(new Set(bucketBoxes.map((b) => b.y)).size).toBe(1);
  for (const b of bucketBoxes) expect(b.h).toBeGreaterThanOrEqual(120);

  const bucketListH = (await page.locator('.repeat-buckets__list').boundingBox())!.height;
  expect(Math.max(...bucketBoxes.map((b) => b.h)) / bucketListH).toBeGreaterThan(0.95);

  // 柱高与人数同向：6 单以上（142 人）必须明显高于 2 单（1 人）
  const barHeights = await page
    .locator('.report-repeat__bucket-bar')
    .evaluateAll((els) => els.map((e) => Math.round(e.getBoundingClientRect().height)));
  expect(barHeights.length).toBe(3);
  expect(barHeights[2]).toBeGreaterThan(barHeights[0] * 3);
  for (const h of barHeights) expect(h).toBeGreaterThan(0);

  // ④ 客户结构环图顶部对齐：环顶距容器顶 ≤ 12px（改前 43px）
  const donut = await page.locator('.mix-donut').boundingBox();
  const mixBody = await page.locator('.mix-body').boundingBox();
  expect(Math.round(donut!.y - mixBody!.y)).toBeLessThanOrEqual(12);
  // 环图尺寸不许为紧凑而缩水（E2E 既有口径 ≥140）
  expect(donut!.width).toBeGreaterThanOrEqual(140);

  // ⑤ 口径说明压紧：字号 12、上边距 ≤ 10
  const hintStyle = await page.locator('.report-risk .sent-hint').evaluate((el) => {
    const cs = getComputedStyle(el);
    return { fontSize: parseFloat(cs.fontSize), marginTop: parseFloat(cs.marginTop) };
  });
  expect(hintStyle.fontSize).toBeLessThanOrEqual(12);
  expect(hintStyle.marginTop).toBeLessThanOrEqual(10);

  // ⑥ 商品明细行高 ≤ 42（改前 48）
  const rowHeights = await page
    .locator('.report-product-detail .el-table__body tr')
    .evaluateAll((els) => els.slice(0, 3).map((e) => Math.round(e.getBoundingClientRect().height)));
  expect(rowHeights.length).toBeGreaterThan(0);
  for (const h of rowHeights) expect(h).toBeLessThanOrEqual(42);

  // ⑦ 履约预警短标签行：数值紧跟标签（不再被 flex:1 推到中部），徽标贴右
  const riskRow = await page.locator('.report-risk__item').last().evaluate((el) => {
    const label = el.querySelector('.report-risk__label') as HTMLElement;
    const value = el.querySelector('.report-risk__value') as HTMLElement;
    const level = el.querySelector('.report-risk__level') as HTMLElement;
    const row = el.querySelector('.report-risk__row') as HTMLElement;
    const lb = label.getBoundingClientRect();
    const vb = value.getBoundingClientRect();
    const lv = level.getBoundingClientRect();
    const rb = row.getBoundingClientRect();
    return {
      labelToValue: Math.round(vb.left - lb.right),
      levelToRight: Math.round(rb.right - lv.right),
    };
  });
  expect(riskRow.labelToValue).toBeLessThanOrEqual(12);
  expect(Math.abs(riskRow.levelToRight)).toBeLessThanOrEqual(2);
});
