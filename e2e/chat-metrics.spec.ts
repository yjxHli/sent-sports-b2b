/**
 * 客服指标（2026-11-18 用户要求）：客服经理按今天 / 近 7 天 / 近 30 天 / 自定义区间，
 * 查看每位客服的接待量、满意率、解决率与平均会话时长。
 * 权限与客服审查同源（chat:audit exact=true），销售等持 '*' 的业务角色不能越权。
 */
import { test, expect, type Page } from '@playwright/test';
import {
  ADMIN_URL, join, apiCall, loginApi, CHAT_MANAGER_NAME, CHAT_MANAGER_PASSWORD,
} from './chat-helpers';

function dayText(offset: number): string {
  const d = new Date();
  d.setDate(d.getDate() - offset);
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

async function loginUi(page: Page, username: string, password: string): Promise<void> {
  await page.goto(join(ADMIN_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(username);
  await page.getByPlaceholder('密码').fill(password);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
}

test('客服指标：经理可查每位客服明细并切换时段，销售越权被拒', async ({ page, request }) => {
  test.setTimeout(120_000);

  const managerToken = await loginApi(request, CHAT_MANAGER_NAME, CHAT_MANAGER_PASSWORD, 'admin');
  const salesToken = await loginApi(request, 'sales', '123456', 'admin');
  const range = `from=${dayText(29)}&to=${dayText(0)}`;

  const forbidden = await apiCall(request, 'GET', `/admin/chat/metrics?${range}`, undefined, salesToken);
  expect(forbidden?.code, 'exact=true 不能吃 "*" 通配').toBe(40301);

  const res = await apiCall(request, 'GET', `/admin/chat/metrics?${range}`, undefined, managerToken);
  expect(res?.code, `客服指标查询失败：${JSON.stringify(res)}`).toBe(0);
  const rows = res?.data?.rows ?? [];
  expect(Array.isArray(rows)).toBe(true);
  expect(rows.length, '至少有一位在职客服').toBeGreaterThan(0);
  for (const key of ['staffId', 'staffName', 'closedCount', 'ratedCount', 'satisfiedCount',
    'satisfactionRate', 'resolvedCount', 'resolutionRate', 'avgDurationSeconds']) {
    expect(rows[0], `指标行缺少字段 ${key}`).toHaveProperty(key);
  }

  await loginUi(page, CHAT_MANAGER_NAME, CHAT_MANAGER_PASSWORD);
  await page.goto(join(ADMIN_URL, '/chat/metrics'));
  await expect(page.getByRole('heading', { name: '客服指标' })).toBeVisible();
  await expect(page.getByTestId('metrics-table')).toBeVisible({ timeout: 20_000 });
  await expect(page.locator('tbody tr').first()).toBeVisible({ timeout: 20_000 });

  const range7 = await page.getByTestId('metrics-range-text').innerText();
  // 全局 zhCn 后要点可见的 .el-radio-button__inner，点透明 input 不触发 change
  await page.locator('.el-radio-button__inner', { hasText: '今天' }).click();
  await expect(page.getByTestId('metrics-range-text')).not.toHaveText(range7, { timeout: 20_000 });
  await page.locator('.el-radio-button__inner', { hasText: '近 30 天' }).click();
  await expect(page.getByTestId('metrics-range-text')).not.toHaveText(range7, { timeout: 20_000 });
});
