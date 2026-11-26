import { test, expect, type Page } from '@playwright/test';
import { ensureHealthySku, ensureWarningSku } from './demo-state';

/**
 * 库存预警无数据（TDD 红→绿）。
 *
 * 用户诉求：/admin/report/stock-warning 打开后摘要全是 0、预警清单「暂无数据」。
 * 根因三层：
 * 1) GET /api/admin/reports/stock-warning 是 P4 占位桩，恒返回 (0, "占位", 0, 0)；
 * 2) 「仅看低于安全库存」是前端把当前页再 filter 一遍（PageQuery 单页上限 100，会漏数据），
 *    total 还用过滤后行数，属于筛选未下推；
 * 3) sku_stock 里 124 行设了 safety_qty，却没有一行告急，勾选后必然空白。
 *
 * 断言口径：默认进页能看到真实台账；勾选后**后端必须重新请求并带 warningOnly=true**，
 * 返回行全部满足 可用 <= 安全库存、总数与行数一致；取消勾选恢复更多行。
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

/** 库存数字表格里带千分位（`1,000`），直接 Number() 会得到 NaN，必须先剥掉非数字字符 */
function num(text: string | undefined): number {
  return Number(String(text ?? '').replace(/[^\d.-]/g, ''));
}

async function totalOf(page: Page): Promise<number> {
  const text = (await page.locator('.sent-table-pager').first().innerText()).replace(/\s+/g, ' ');
  const m = text.match(/共\s*(\d+)\s*条/);
  return m ? Number(m[1]) : -1;
}

test('库存预警：默认有真实台账，仅看预警由后端下推且有真实告警行', async ({ page }) => {
  // 两个绝对数断言要现场保底：① 至少 1 行低于安全库存（买空／补库存后可能为 0）；
  // ② 至少 1 行不预警（全库都预警时「预警条数 < 全量条数」不成立）。库健康时两者都不写库。
  const restoreWarning = ensureWarningSku();
  const restoreHealthy = ensureHealthySku();
  try {
    await login(page);
    await page.goto(ADMIN_URL + 'report/stock-warning');
    await page.waitForSelector('.el-table__body-wrapper tbody tr', { timeout: 15000 });
    await page.waitForTimeout(1200);

    // ① 摘要必须是真查库的结果，不再是占位值
    const summaryText = await page.locator('.el-descriptions').first().innerText();
    console.log('预警摘要:', summaryText.replace(/\s+/g, ' '));
    expect(summaryText).not.toContain('占位');
    const skuMatch = summaryText.replace(/\s+/g, ' ').match(/SKU\s*(\d+)/);
    expect(Number(skuMatch?.[1] ?? 0)).toBeGreaterThan(0);

    // ② 默认清单有真实库存行（不再是「暂无数据」）
    const allRows = await page.locator('.el-table__body-wrapper tbody tr').count();
    const allTotal = await totalOf(page);
    console.log('默认台账行数:', allRows, '共:', allTotal);
    expect(allRows).toBeGreaterThan(0);
    expect(allTotal).toBeGreaterThan(0);
    await expect(page.getByText('暂无数据')).toHaveCount(0);

    // ③ 勾选「仅看低于安全库存」：必须触发带 warningOnly=true 的后端请求
    const checkbox = page.locator('.el-checkbox', { hasText: '仅看低于安全库存' }).first();
    await expect(checkbox).toBeVisible();
    const [response] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/admin/stock')
          && r.url().includes('warningOnly=true')
          && r.request().method() === 'GET',
        { timeout: 15000 },
      ),
      checkbox.click(),
    ]);
    expect(response.status()).toBe(200);
    await page.waitForTimeout(900);

    const warnRows = await page.locator('.el-table__body-wrapper tbody tr').count();
    const available = await cellTexts(page, 7);
    const safe = await cellTexts(page, 8);
    const statuses = await cellTexts(page, 9);
    const warnTotal = await totalOf(page);
    console.log('预警行数:', warnRows, '共:', warnTotal, '状态:', statuses.join(','));

    expect(warnRows).toBeGreaterThan(0);
    expect(warnRows).toBeLessThanOrEqual(allRows);
    expect(warnTotal).toBeGreaterThan(0);
    expect(warnTotal).toBeLessThan(allTotal);
    for (let i = 0; i < warnRows; i++) {
      expect(num(available[i]), `第 ${i + 1} 行可用量`).toBeLessThanOrEqual(num(safe[i]));
      expect(statuses[i]).toBe('预警');
    }

    // ④ 取消勾选恢复全量，行数回到更多
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/admin/stock') && r.request().method() === 'GET',
        { timeout: 15000 },
      ),
      checkbox.click(),
    ]);
    await page.waitForTimeout(900);
    const restoredRows = await page.locator('.el-table__body-wrapper tbody tr').count();
    console.log('恢复后行数:', restoredRows);
    expect(restoredRows).toBeGreaterThanOrEqual(warnRows);
    expect(await totalOf(page)).toBe(allTotal);
  } finally {
    restoreHealthy();
    restoreWarning();
  }
});
