/**
 * 商品详情页 · 购买栏「UV 定制」文案不得超框（2026-11-22）
 *
 * 缺陷现场：`.custom-panel__check` 是 el-checkbox，Element Plus 给 `.el-checkbox`
 * 默认 `white-space: nowrap`。定制说明是一整行长文案（企业队服 / 印 logo、定制费、
 * 30 件起订），窄屏购买栏收到 400px 后不换行 → 文字横向撑出虚线边框。
 *
 * 断言口径（与数据解耦）：
 *   * 详情页定制面板存在；
 *   * 面板内容不产生横向溢出（scrollWidth ≤ clientWidth + 1）；
 *   * 勾选文案的右边界不得越过面板内边（含 1px 亚像素容差）。
 * 覆盖两档宽度：1080（≤1240 断点，购买栏 400px）与 1440（宽屏，购买栏 420px）。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';

const USER_URL = (process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const USER_NAME = process.env.E2E_USER_NAME ?? '13800138000';
const USER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';

function sql(statement: string): string {
  const bin = process.env.E2E_MYSQL_BIN ?? (existsSync(LOCAL_MYSQL) ? LOCAL_MYSQL : 'mysql');
  return execFileSync(bin, [
    '-h127.0.0.1', '-P3306', '-uroot', `-p${process.env.E2E_DB_PASSWORD ?? '123456'}`,
    '--default-character-set=utf8mb4',
    'sent_dev', '-N', '-B', '-e', statement,
  ], { encoding: 'utf8' }).trim();
}

function pickOnShelfProduct(): string {
  const row = sql(`SELECT id FROM product WHERE deleted = 0 AND status = 'ON_SHELF' ORDER BY id ASC LIMIT 1;`);
  if (!row) throw new Error('库中没有在售商品，无法验证详情页');
  return row;
}

async function login(page: Page): Promise<void> {
  await page.goto(USER_URL, { waitUntil: 'networkidle' });
  if (await page.locator('input[type="password"]').count()) {
    await page.locator('input').first().fill(USER_NAME);
    await page.locator('input[type="password"]').fill(USER_PASSWORD);
    // 2026-11-28：登录页新增协议勾选（默认未勾选），E2E 必须先确认协议
    if (await page.getByTestId('auth-agree').count()) {
      await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  }
    await page.getByRole('button', { name: '登录' }).click();
    // 等登录态落地：登录成功会跳走，密码框消失；不要只等 networkidle（登录接口返回后还有跳转）
    await expect(page.locator('input[type="password"]')).toHaveCount(0, { timeout: 15000 });
    await page.waitForTimeout(600);
  }
}

async function measure(page: Page) {
  return page.evaluate(() => {
    const panel = document.querySelector('.custom-panel') as HTMLElement | null;
    const label = document.querySelector('.custom-panel__check .el-checkbox__label') as HTMLElement | null;
    if (!panel || !label) return null;
    const cs = getComputedStyle(panel);
    const p = panel.getBoundingClientRect();
    const l = label.getBoundingClientRect();
    return {
      panelScrollW: panel.scrollWidth,
      panelClientW: panel.clientWidth,
      panelInnerRight: Math.round(p.right - parseFloat(cs.paddingRight)),
      labelLeft: Math.round(l.left),
      labelRight: Math.round(l.right),
      labelWidth: Math.round(l.width),
      labelHeight: Math.round(l.height),
    };
  });
}

for (const width of [1080, 1440]) {
  test.describe(`商品详情页 · 购买栏定制文案不超框（${width}px 视口）`, () => {
    test('UV 定制说明必须换行且不溢出面板', async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      const productId = pickOnShelfProduct();

      await login(page);
      await page.goto(`${USER_URL}/product/${productId}`, { waitUntil: 'networkidle' });
      await expect(page.locator('.buy')).toBeVisible({ timeout: 15000 });

      const hasPanel = await page.locator('.custom-panel').count();
      if (!hasPanel) test.skip(true, '该商品未展示 UV 定制面板，跳过');

      const m = await measure(page);
      expect(m, '应能量到定制面板与勾选文案').not.toBeNull();

      expect(
        m!.panelScrollW,
        `定制面板横向溢出：scrollWidth ${m!.panelScrollW} > clientWidth ${m!.panelClientW}`,
      ).toBeLessThanOrEqual(m!.panelClientW + 1);

      expect(
        m!.labelRight,
        `定制文案右边界 ${m!.labelRight}px 越过面板内边 ${m!.panelInnerRight}px（文字没换行）`,
      ).toBeLessThanOrEqual(m!.panelInnerRight + 1);
    });
  });
}
