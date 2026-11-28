/**
 * 商城登录必须先勾选「已阅读并同意」（2026-11-28 用户需求）。
 *
 * 用户口径：不点同意框不能登录成功，并且要提示用户先确认协议。
 *
 * TDD：本 spec 先于前端拦截逻辑建立。未实现时「默认未勾选」「未勾选拦截并提示」必红。
 */
import { test, expect } from '@playwright/test';
import { USER_URL, join } from './chat-helpers';

const PHONE = '13800138000';
const PASSWORD = '123456';

test('未勾选协议不能登录且会提示，勾选后可正常登录', async ({ page }) => {
  test.setTimeout(90_000);

  let loginRequested = false;
  page.on('request', (r) => {
    if (r.method() === 'POST' && r.url().includes('/api/auth/login')) loginRequested = true;
  });

  await page.goto(join(USER_URL, '/login'));
  const agree = page.getByTestId('auth-agree');
  await expect(agree, '协议默认必须是未勾选状态').not.toHaveClass(/is-checked/, { timeout: 15_000 });

  await page.getByTestId('auth-phone').fill(PHONE);
  await page.getByTestId('auth-password').fill(PASSWORD);
  await page.getByTestId('auth-login-submit').click();

  // 必须给出明确的提示，且不能悄悄发起登录请求
  await expect(page.locator('.el-message', { hasText: '请先勾选' })).toBeVisible({ timeout: 8_000 });
  await expect(page.locator('.el-message', { hasText: '已阅读并同意' })).toBeVisible({ timeout: 8_000 });
  expect(loginRequested, '未勾选协议时不应发出登录请求').toBe(false);
  await expect(page, '未勾选时应停留在登录页').toHaveURL(/\/login(\?|$)/);

  // 勾选后同一组凭证可以正常登录
  // 复选框 label 中间是协议链接（点击会打开弹窗），必须点方框本体
  await agree.locator('.el-checkbox__inner').click();
  await expect(agree).toHaveClass(/is-checked/);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByTestId('auth-login-submit').click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
});
