/**
 * 商城注册（2026-11-18 用户要求）：手机号 + 密码 + 确认密码；注册成功自动登录，
 * 客户号由服务端分配（用户不可见），默认昵称取手机号尾四位、默认头像由前端首字兜底。
 *
 * TDD：本用例先于注册前端/后端能力建立，未实现时因找不到「注册」Tab 直接红。
 */
import { test, expect, type Page } from '@playwright/test';
import { USER_URL, join, apiCall } from './chat-helpers';

const PHONE = `139${String(Date.now()).slice(-8)}`;
const PASSWORD = 'Reg@123456';

test('商城注册：两次密码校验、成功自动登录、默认昵称与头像', async ({ page, request }) => {
  test.setTimeout(90_000);

  await page.goto(join(USER_URL, '/login'));
  await page.getByTestId('login-mode-register').click();
  await page.getByTestId('auth-phone').fill(PHONE);
  await page.getByTestId('auth-password').fill(PASSWORD);

  // 两次输入不一致：前端先拦一道，不发注册请求
  await page.getByTestId('auth-confirm-password').fill('Different1');
  await page.getByTestId('auth-register-submit').click();
  await expect(page.locator('.el-message', { hasText: '两次输入的密码不一致' })).toBeVisible({ timeout: 8_000 });

  await page.getByTestId('auth-confirm-password').fill(PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/register') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByTestId('auth-register-submit').click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `注册失败：${JSON.stringify(body)}`).toBe(0);
  expect(Number(body?.data?.customerId)).toBeGreaterThan(0);
  expect(body?.data?.nickname).toBe(`用户${PHONE.slice(-4)}`);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });

  // 自动登录：token 已可用于业务接口；个人资料昵称是默认昵称，头像是默认（空 fileId）
  const me = await apiCall(request, 'GET', '/me', undefined,
    (await page.evaluate(() => localStorage.getItem('sent_user_access_token'))) ?? undefined);
  expect(me?.code, `注册后应已自动登录：${JSON.stringify(me)}`).toBe(0);
  expect(me?.data?.nickname).toBe(`用户${PHONE.slice(-4)}`);
  expect(me?.data?.avatarFileId ?? null, '默认头像由前端首字兜底，库里不存图片').toBeFalsy();

  // 重复注册同手机号被唯一索引拒绝，不能生成第二个账号
  const dup = await apiCall(request, 'POST', '/auth/register', {
    phone: PHONE, password: PASSWORD, confirmPassword: PASSWORD,
  });
  expect(dup?.code, '防重只靠 DB 唯一索引，重复手机号必须失败').not.toBe(0);
});

/** 演示账号登录入口不能被注册流程破坏 */
test('注册 Tab 可切回登录，演示账号仍可登录', async ({ page }: { page: Page }) => {
  await page.goto(join(USER_URL, '/login'));
  await page.getByTestId('login-mode-register').click();
  await expect(page.getByTestId('auth-confirm-password')).toBeVisible();
  await page.getByTestId('login-mode-login').click();
  await expect(page.getByTestId('auth-confirm-password')).toHaveCount(0);
  await page.getByTestId('auth-phone').fill('13800138000');
  await page.getByTestId('auth-password').fill('123456');
  // 2026-11-28：登录页新增协议勾选（默认未勾选），E2E 必须先确认协议
  if (await page.getByTestId('auth-agree').count()) {
    await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  }
  await page.getByTestId('auth-login-submit').click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
});
