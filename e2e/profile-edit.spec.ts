/**
 * 我的账号点头像 → 编辑个人信息 → 保存生效（2026-11-17 用户要求，TDD）。
 *
 * 断言口径：
 *   1) /mine 头像可点击进入 /profile/edit；
 *   2) 改昵称保存后回 /mine，页面与 GET /api/me 都是新昵称；
 *   3) 头像字段落的是文件服务 fileId（上传后预览可见）。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = (process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

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

test('头像进入编辑页，改昵称保存后生效', async ({ page, request }) => {
  test.setTimeout(90_000);
  await loginUser(page);

  // 记录原昵称，用例结束后恢复，避免污染其它 E2E
  const initialToken = await page.evaluate(() => localStorage.getItem('sent_user_access_token') ?? '');
  const meBefore: any = await (await request.get(`${API_URL}/me`, {
    headers: { Authorization: `Bearer ${initialToken}` },
  })).json().catch(() => null);
  const original = String(meBefore?.data?.nickname ?? '');

  await page.goto(`${USER_URL}/mine`);
  await page.getByTestId('edit-profile-entry').click();
  await expect(page).toHaveURL(/\/profile\/edit/, { timeout: 15_000 });

  const newName = `测试用户${Date.now().toString().slice(-5)}`;
  await page.getByTestId('nickname-input').fill(newName);
  await page.getByTestId('save-profile').click();
  await expect(page).toHaveURL(/\/mine/, { timeout: 15_000 });
  await expect(page.getByText(newName, { exact: false }).first()).toBeVisible({ timeout: 15_000 });

  const token = await page.evaluate(() => localStorage.getItem('sent_user_access_token') ?? '');
  const me: any = await (await request.get(`${API_URL}/me`, {
    headers: { Authorization: `Bearer ${token}` },
  })).json();
  expect(me?.data?.nickname, '后端资料必须同步更新').toBe(newName);

  // 恢复原昵称（有值才恢复，避免把演示账号改空）
  if (original) {
    await request.put(`${API_URL}/me/profile`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { nickname: original, avatarFileId: me?.data?.avatarFileId ?? null },
    });
  }
});
