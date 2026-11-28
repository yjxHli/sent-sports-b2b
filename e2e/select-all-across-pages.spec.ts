/**
 * 「全选 = 全部」（2026-11-26 用户要求）。
 *
 * 用户原话：「如果点击全选框是所有记录统一勾选，不是一页一页的，效率太低
 * （所有跟管理端客服审查类似的删除功能如果点击全选框都是所有内容统一全选）」；
 * 商城端「购物车和我的订单的删除功能也是如果全选的话，全部选择」。
 *
 * 本 spec 只验证「选中范围」，**不执行任何删除**：
 *   1) 管理端：客户列表切到每页 10 条（总数 > 10），点表头全选框 → 已选条数 = 全表总数；
 *   2) 商城端：我的订单点「全选全部」→ 已选笔数 > 本页可删卡片数（跨页），且不超过总笔数。
 */
import { test, expect, type Page } from '@playwright/test';

const ADMIN_URL = (process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/').replace(/\/+$/, '');
const USER_URL = (process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const ADMIN_NAME = process.env.E2E_ADMIN_NAME ?? 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD ?? '123456';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

async function loginAdmin(page: Page): Promise<void> {
  await page.goto(`${ADMIN_URL}/login`);
  await page.getByPlaceholder('用户名').fill(ADMIN_NAME);
  await page.getByPlaceholder('密码').fill(ADMIN_PASSWORD);
  // 2026-11-28：登录页新增协议勾选（默认未勾选），E2E 必须先确认协议
  if (await page.getByTestId('auth-agree').count()) {
    await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  }
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  // 2026-11-28：登录页新增协议勾选（默认未勾选），E2E 必须先确认协议
  if (await page.getByTestId('auth-agree').count()) {
    await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  }
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

test('管理端：表头全选 = 当前筛选下全部记录（跨页），不是只选本页', async ({ page }) => {
  await loginAdmin(page);
  await page.goto(`${ADMIN_URL}/customer/list`);
  await expect(page.locator('.sent-table-pager')).toBeVisible({ timeout: 20_000 });

  // 切到每页 10 条，确保总数 &gt; 本页行数，能真正验证「跨页」
  await page.locator('.sent-table-pager .el-select').first().click();
  await page.locator('.el-select-dropdown__item:visible', { hasText: '10' }).first().click();
  await page.waitForTimeout(600);

  const totalText = await page.locator('.sent-table-pager .el-pagination__total').first().innerText();
  const total = Number((totalText.match(/\d+/) ?? ['0'])[0]);
  const pageRows = await page.locator('.el-table__body tbody tr').count();
  expect(total, '演示库客户数应多于一页（否则本用例说明不了跨页）').toBeGreaterThan(pageRows);

  // 进入删除态 → 点表头全选框
  await page.getByTestId('batch-delete-btn').click();
  await expect(page.getByTestId('batch-delete-cancel')).toBeVisible();
  await page.locator('thead .el-checkbox').first().click();

  // 已选 = 全表总数，而不是本页的 10 条
  await expect(page.getByTestId('batch-delete-tip')).toContainText(`已选 ${total} 条`, { timeout: 20_000 });

  // 只验证范围，不删除（取消退出删除态）
  await page.getByTestId('batch-delete-cancel').click();
});

test('商城端：我的订单全选 = 当前筛选下全部可删订单', async ({ page }) => {
  await loginUser(page);
  // 「已完成」全部可删；默认 Tab 里最新几笔多为在途，全选按钮会被禁用（符合预期）
  await page.goto(`${USER_URL}/orders?status=COMPLETED`);
  await expect(page.locator('.order-card').first()).toBeVisible({ timeout: 20_000 });

  const totalText = await page.locator('.order-tabs__total').innerText();
  const total = Number((totalText.match(/\d+/) ?? ['0'])[0]);
  expect(total, '演示库应至少有 1 笔已完成订单').toBeGreaterThan(0);
  const cardsOnPage = await page.locator('.order-card').count();

  await page.getByTestId('order-batch-toggle').click();
  await page.getByTestId('order-batch-all').click();

  // 全选要走「拉全量」请求，不能读完立即取值
  const hintLocator = page.getByTestId('order-batch-bar').locator('.batch-bar__hint');
  await expect(hintLocator).toContainText(`已选 ${total} 笔`, { timeout: 30_000 });
  const hint = await hintLocator.innerText();
  const selected = Number((hint.match(/已选\s*(\d+)/) ?? ['0', '0'])[1]);
  // 已完成单全部可删 → 全选必须覆盖该 Tab 的全部订单，而不是本页
  expect(selected, '全选应覆盖全部，而不是本页').toBe(total);
  if (total > cardsOnPage) {
    expect(selected, '跨页时已选数必须大于本页卡片数').toBeGreaterThan(cardsOnPage);
  }
});
