/**
 * 订单列表删除范围可选（2026-11-26 用户诉求，TDD 先红后绿）。
 *
 * 用户口径：「删除功能可以加一个全选删除，也可以选择整页删除，更灵活一点」。
 * 本用例只验证勾选范围，不执行删除，避免污染演示库：
 *   - 全选本页：已选条数 = 当前页行数；
 *   - 全选全部：已选条数 = 当前筛选下总条数（跨页）。
 */
import { test, expect, type Page } from '@playwright/test';

const ADMIN_URL = (process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/').replace(/\/+$/, '');

async function login(page: Page): Promise<void> {
  await page.goto(`${ADMIN_URL}/login`);
  await page.getByPlaceholder('用户名').fill('admin');
  await page.getByPlaceholder('密码').fill('123456');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

test('订单列表：删除态可分别选择整页与跨页全部', async ({ page }) => {
  await login(page);
  await page.goto(`${ADMIN_URL}/order/list`);
  await expect(page.locator('.sent-table-pager')).toBeVisible({ timeout: 20_000 });

  // 每页 10 条，保证「本页 < 总数」，两个范围能真正区分开
  await page.locator('.sent-table-pager .el-select').first().click();
  await page.locator('.el-select-dropdown__item:visible', { hasText: '10' }).first().click();
  await expect.poll(async () => page.locator('.el-table__body tbody tr').count()).toBe(10);

  const totalText = await page.locator('.sent-table-pager .el-pagination__total').first().innerText();
  const total = Number((totalText.match(/\d+/) ?? ['0'])[0]);
  const pageRows = await page.locator('.el-table__body tbody tr').count();
  expect(total, '演示库订单应多于一页').toBeGreaterThan(pageRows);

  await page.getByTestId('batch-delete-btn').click();
  await expect(page.getByTestId('batch-delete-cancel')).toBeVisible();

  // 整页删除：只勾当前页
  const selectPage = page.getByTestId('batch-select-page');
  const selectAll = page.getByTestId('batch-select-all');
  await expect(selectPage).toBeVisible({ timeout: 5_000 });
  await selectPage.click();
  await expect(page.getByTestId('batch-delete-tip'))
    .toContainText(`已选 ${pageRows} 条`, { timeout: 10_000 });

  // 全选删除：按当前筛选拉全量，覆盖所有分页
  await selectAll.click();
  await expect(page.getByTestId('batch-delete-tip'))
    .toContainText(`已选 ${total} 条`, { timeout: 20_000 });

  // 只验范围不删除，取消退出删除态
  await page.getByTestId('batch-delete-cancel').click();
  await expect(page.getByTestId('batch-delete-btn')).toBeVisible();
});
