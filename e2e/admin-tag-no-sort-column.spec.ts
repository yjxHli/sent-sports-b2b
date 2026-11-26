/**
 * 客户标签：取消序号/排序列（2026-11-26 用户诉求）。
 * 只校验列顺序与表头，不改任何标签数据。
 */
import { test, expect, type Page } from '@playwright/test';

const ADMIN_URL = (process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/').replace(/\/+$/, '');

async function loginAdmin(page: Page): Promise<void> {
  await page.goto(`${ADMIN_URL}/login`);
  await page.getByPlaceholder('用户名').fill('admin');
  await page.getByPlaceholder('密码').fill('123456');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

test('客户标签：不再展示排序列，直接从标签名开始', async ({ page }) => {
  await loginAdmin(page);
  await page.goto(`${ADMIN_URL}/customer/tags`);

  const list = page.getByTestId('tag-list');
  const firstRow = list.locator('tbody tr').first();
  await expect(firstRow).toBeVisible({ timeout: 20_000 });

  const headers = await list.locator('.el-table__header-wrapper th .cell').allInnerTexts();
  const trimmed = headers.map((h) => h.trim());
  expect(trimmed.slice(0, 3)).toEqual(['标签名', '分类', '状态']);
  expect(trimmed, '排序列应已移除').not.toContain('排序');
  expect(trimmed, '序号列应已移除').not.toContain('序号');

  const firstCell = firstRow.locator('td .cell').first();
  await expect(firstCell.locator('.tag-chip')).toBeVisible();
});
