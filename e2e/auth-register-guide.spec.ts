/**
 * 登录引导闭环 E2E（TDD）：
 * 1) 未注册手机号登录 → 友好提示 → 跳 /register → 手机号回填 → 补全注册 → 自动登录跳首页
 * 2) 密码/验证码 Tab 切换 + 获取验证码 + 60s 倒计时置灰防连点
 */
import { test, expect } from '@playwright/test';
import { USER_URL, join } from './chat-helpers';

const PASSWORD = 'Reg@123456';

test('未注册手机号登录被拦截，跳注册页并回填手机号', async ({ page }) => {
  test.setTimeout(90_000);
  const phone = `139${String(Date.now()).slice(-8)}`;

  await page.goto(join(USER_URL, '/login'));
  await page.getByTestId('auth-phone').fill(phone);
  await page.getByTestId('auth-password').fill('AnyPassword1');
  await page.getByTestId('auth-login-submit').click();

  await expect(page.locator('.el-message', { hasText: '该手机号尚未注册，请先注册' }))
    .toBeVisible({ timeout: 10_000 });
  await expect(page).toHaveURL(/\/register(\?|$)/, { timeout: 10_000 });
  await expect(page.getByTestId('auth-phone')).toHaveValue(phone);
  await expect(page.getByTestId('auth-register-submit')).toBeVisible();

  // 补全必填项后注册成功并自动登录
  await page.getByTestId('auth-password').fill(PASSWORD);
  await page.getByTestId('auth-confirm-password').fill(PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/register') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByTestId('auth-register-submit').click();
  expect((await (await resp).json())?.code).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
  expect(await page.evaluate(() => localStorage.getItem('sent_user_access_token'))).toBeTruthy();
});

test('验证码登录 Tab 切换、获取验证码与 60s 倒计时', async ({ page }) => {
  test.setTimeout(60_000);
  const phone = `139${String(Date.now()).slice(-8)}`;

  await page.goto(join(USER_URL, '/login'));
  await page.getByTestId('login-tab-code').click();
  await expect(page.getByTestId('auth-code')).toBeVisible();
  await expect(page.getByTestId('auth-password')).toHaveCount(0);
  await page.getByTestId('auth-phone').fill(phone);

  // 浏览器控制台应打印验证码（DEV + 后端 dev 开关）
  const consoleLogs: string[] = [];
  page.on('console', (m) => { if (m.type() === 'info') consoleLogs.push(m.text()); });

  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/sms-code') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByTestId('auth-send-code').click();
  const body: any = await (await resp).json();
  expect(body?.code).toBe(0);
  expect(body?.data?.demoCode, 'dev 环境后端应回显 demoCode').toMatch(/^\d{6}$/);
  await expect.poll(() => consoleLogs.some((t) => t.includes(body.data.demoCode)), { timeout: 8_000 })
    .toBe(true);

  // 按钮置灰并显示倒计时
  const sendBtn = page.getByTestId('auth-send-code');
  await expect(sendBtn).toBeDisabled();
  await expect(sendBtn).toContainText('后重发');

  // 切回密码登录，验证码字段消失
  await page.getByTestId('login-tab-password').click();
  await expect(page.getByTestId('auth-code')).toHaveCount(0);
  await expect(page.getByTestId('auth-password')).toBeVisible();
});
