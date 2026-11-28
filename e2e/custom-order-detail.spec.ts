/**
 * 定制单列表 → 详情页（TDD 红→绿）。
 *
 * 用户诉求（2026-11-15）：商城端「我的定制」里，每条定制单都应该能点进去查看详情，
 * 而不是只在列表下方展开一小块、或者根本点不动。
 *
 * 断言口径：
 *   1) 点列表第一条 → URL 变成 /custom/<定制单号>（独立详情页，不是页内展开）；
 *   2) 详情页标题就是该定制单号，状态标签与列表一致；
 *   3) 详情页有图稿区块（有稿则列出图稿版本，无稿显示空态提示）与「上传图稿」入口；
 *   4) 详情页有基础信息（关联订单 / 图稿版本 / 驳回次数）；
 *   5) 能返回列表，且全程无 console error。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

const pathOf = (page: Page): string => new URL(page.url()).pathname;

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  // 2026-11-28：登录页新增协议勾选（默认未勾选），E2E 必须先确认协议
  if (await page.getByTestId('auth-agree').count()) {
    await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  }
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

test('点定制单进入独立详情页，能看图稿与审核信息并返回列表', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/custom`);

  const firstRow = page.locator('.custom-row').first();
  await expect(firstRow, '定制单列表应有数据（演示库有 9002 的历史定制单）').toBeVisible({
    timeout: 20_000,
  });

  // 记下这一行的单号与状态文案，详情页必须一致
  const customNo = (await firstRow.locator('.row-main strong').innerText()).trim();
  const statusText = (await firstRow.locator('.el-tag').first().innerText()).trim();
  expect(customNo, '列表行应展示定制单号').toMatch(/^CU\d+/);

  // 1) 点击 → 独立详情页
  await firstRow.click();
  await expect
    .poll(() => pathOf(page), {
      timeout: 20_000,
      message: '点定制单应进入 /custom/<定制单号> 详情页，而不是原地展开',
    })
    .toBe(`/custom/${customNo}`);

  // 2) 标题与状态
  await expect(page.locator('.custom-detail')).toBeVisible({ timeout: 20_000 });
  await expect(page.locator('.custom-detail__no')).toContainText(customNo);
  await expect(page.locator('.custom-detail__status')).toContainText(statusText);

  // 3) 图稿区块 + 上传入口（有稿列版本，无稿给空态）
  const drafts = page.locator('.draft-item');
  const emptyDraft = page.locator('.draft-empty');
  await expect
    .poll(async () => (await drafts.count()) + (await emptyDraft.count()), {
      timeout: 20_000,
      message: '详情页应展示图稿版本列表或空态提示',
    })
    .toBeGreaterThan(0);
  await expect(page.getByRole('button', { name: /上传图稿|重新上传/ })).toBeVisible({
    timeout: 20_000,
  });

  // 4) 基础信息：关联订单 / 图稿版本 / 驳回次数
  const info = page.locator('.custom-detail__info');
  await expect(info).toContainText('关联订单');
  await expect(info).toContainText('图稿版本');
  await expect(info).toContainText('驳回');

  // 5) 返回列表
  await page.locator('.custom-detail__back').click();
  await expect.poll(() => pathOf(page), { timeout: 20_000 }).toBe('/custom');
  await expect(page.locator('.custom-row').first()).toBeVisible({ timeout: 20_000 });

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});
