import { test, expect, type Page } from '@playwright/test';
import { sqlNumber } from './db';

/**
 * 2026-11-27 库存单据 UI 重构的聚焦回归（TDD 先红后绿）。
 * 只覆盖前端交互：三页共用一个「可输入 + 可下拉选择」的 SKU 控件，
 * 手输数字 ID 与下拉选规格都写回同一个数字 modelValue。
 * 不提交任何单据，不依赖演示库存数量。
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

const STOCK_PAGES = [
  ['stock/inbound', '手工入库'],
  ['stock/loss', '库存报损'],
  ['stock/check', '库存盘点'],
] as const;

test('三页 SKU 字段统一为可输可选的选件，不再是数字步进器', async ({ page }) => {
  await login(page);
  for (const [path, title] of STOCK_PAGES) {
    await page.goto(ADMIN_URL + path);
    await expect(page.getByRole('heading', { name: title })).toBeVisible();
    await expect(page.locator('.sku-picker input')).toBeVisible();
  }
});

test('SKU 支持直接手输数字 ID 并同步到表单值', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'stock/inbound');
  const skuId = sqlNumber(
    'SELECT id FROM sku WHERE deleted IS NULL OR deleted = 0 ORDER BY id LIMIT 1',
    9101,
  );

  const picker = page.locator('.sku-picker');
  await expect(picker.locator('input')).toHaveValue('');
  await picker.locator('input').fill(String(skuId));
  await expect(picker).toHaveAttribute('data-sku-id', String(skuId));
});

test('SKU 支持按商品名搜索并从下拉选择规格', async ({ page }) => {
  await login(page);
  await page.goto(ADMIN_URL + 'stock/loss');
  const picker = page.locator('.sku-picker');
  await picker.locator('input').fill('训练');

  const option = page.locator('.el-autocomplete-suggestion li').filter({ hasText: 'SKU' }).first();
  await expect(option).toBeVisible();
  await option.click();

  const chosen = Number(await picker.getAttribute('data-sku-id'));
  expect(chosen).toBeGreaterThan(0);
  await expect(picker.locator('input')).toHaveValue(String(chosen));
});
