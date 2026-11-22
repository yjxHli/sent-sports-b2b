import { test, expect, type Page } from '@playwright/test';

/**
 * 2026-11-21 用户截图反馈的回归用例（TDD 先红后绿）：
 * 1) 库存预警从「报表」分组挪进「库存」分组；
 * 2) 销售报表更名「业务报表」，作为与工作台同级的一级菜单紧跟工作台；
 * 3) 表格右上角的删除/刷新/列显隐/导出按钮不能是灰得看不清的文字；
 *    表头也不能是偏绿发脏的浅底，文字对比度要够。
 *
 * URL 与权限码保持不变（report/sales、report/stock-warning），
 * 只调整菜单呈现与样式，既有 E2E 不受影响。
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

function rgb(value: string): [number, number, number] {
  const m = value.match(/\d+/g);
  return [Number(m?.[0] ?? 0), Number(m?.[1] ?? 0), Number(m?.[2] ?? 0)];
}

function luminance(value: string): number {
  const [r, g, b] = rgb(value).map((c) => {
    const s = c / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  }) as [number, number, number];
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrast(fg: string, bg: string): number {
  const a = luminance(fg);
  const b = luminance(bg);
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

test('侧栏：业务报表紧跟工作台，库存预警归到库存分组', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'dashboard');
  await page.waitForSelector('.sent-menu .el-menu-item');

  const roots = await page.locator('.sent-menu > .el-menu-item').allInnerTexts();
  expect(roots[0].trim()).toBe('工作台');
  expect(roots[1].trim()).toBe('业务报表');

  const stockGroup = page.locator('.sent-menu > .el-sub-menu').filter({
    has: page.locator('.el-sub-menu__title', { hasText: '库存' }),
  });
  await expect(stockGroup.locator('.el-menu-item', { hasText: '库存预警' })).toHaveCount(1);

  // 页面标题与菜单名一致（E2E 靠 getByRole('heading') 唯一定位）
  await page.goto(ADMIN_URL + 'report/sales');
  await expect(page.getByRole('heading', { name: '业务报表' })).toBeVisible();
});

test('表格：工具栏操作文字与表头文字对比度达标，表头为中性浅底', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'order/list');
  await page.waitForSelector('.sent-table__tool');
  await page.waitForSelector('.el-table th.el-table__cell');

  const sample = await page.evaluate(() => {
    const tool = document.querySelector('.sent-table__tool') as HTMLElement;
    const th = document.querySelector('.el-table th.el-table__cell') as HTMLElement;
    return {
      toolColor: getComputedStyle(tool).color,
      toolBg: getComputedStyle(document.querySelector('.sent-panel') ?? document.body).backgroundColor,
      thColor: getComputedStyle(th).color,
      thBg: getComputedStyle(th).backgroundColor,
    };
  });

  expect(contrast(sample.toolColor, 'rgb(255, 255, 255)')).toBeGreaterThanOrEqual(4.5);
  expect(contrast(sample.thColor, sample.thBg)).toBeGreaterThanOrEqual(4.5);

  // 中性浅底：三个通道最大差 <= 3，亮度 >= 0.92（不再是 #eef0eb 那种偏绿的脏灰）
  const [r, g, b] = rgb(sample.thBg);
  expect(Math.max(r, g, b) - Math.min(r, g, b)).toBeLessThanOrEqual(3);
  expect(luminance(sample.thBg)).toBeGreaterThanOrEqual(0.92);
});
