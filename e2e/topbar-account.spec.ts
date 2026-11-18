/**
 * 商城顶栏账户下拉（2026-11-18 用户要求）：左上角展示真实账户名，
 * hover 展开菜单，可进个人账户或退出登录。
 */
import { test, expect, type Page } from '@playwright/test';
import { USER_URL, join, apiCall, CUSTOMER_PHONE, CUSTOMER_PASSWORD } from './chat-helpers';

async function login(page: Page): Promise<void> {
  await page.goto(join(USER_URL, '/login'));
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

test('顶栏账户名来自 /api/me，下拉可进个人账户并退出登录', async ({ page, request }) => {
  test.setTimeout(120_000);
  await login(page);

  const token = await page.evaluate(
    () => localStorage.getItem('sent_user_access_token')?.replace(/^"|"$/g, '') ?? '',
  );
  const me = await apiCall(request, 'GET', '/me', undefined, token);
  const nickname = String(me?.data?.nickname ?? '');
  expect(nickname).toBeTruthy();

  await page.goto(join(USER_URL, '/home'));
  const account = page.getByTestId('topbar-account');
  await expect(account).toBeVisible({ timeout: 15_000 });
  await expect(page.getByTestId('topbar-account-name')).toContainText(nickname, { timeout: 15_000 });

  await account.hover();
  await expect(page.getByTestId('topbar-account-link')).toBeVisible({ timeout: 8_000 });
  await page.getByTestId('topbar-account-link').click();
  await expect(page).toHaveURL(/\/mine(\?|$)/, { timeout: 15_000 });
  await expect(page.locator('.profile-name')).toContainText(nickname);

  await page.goto(join(USER_URL, '/home'));
  await account.hover();
  await page.getByTestId('topbar-logout').click();
  const dialog = page.locator('.el-message-box');
  await expect(dialog).toBeVisible({ timeout: 10_000 });
  await dialog.getByRole('button', { name: /^(确认|确定)$/ }).click();
  await expect(page).toHaveURL(/\/login(\?|$)/, { timeout: 15_000 });
  await expect(page.getByTestId('topbar-account')).toHaveCount(0);
});
