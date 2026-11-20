/**
 * 找回密码验证码 E2E（TDD 验收）：
 * 与登录验证码同一套机制——后端生成并存内存、打印后端控制台、dev 回显到浏览器控制台、
 * 60s 倒计时防连点、一次性消费；重置成功后新密码立即生效。
 */
import { test, expect } from '@playwright/test';
import { USER_URL, join, apiCall } from './chat-helpers';

test('找回密码验证码与登录同机制，且重置后新密码立即生效', async ({ page, request }) => {
  test.setTimeout(120_000);
  const phone = `137${String(Date.now()).slice(-8)}`;
  const oldPassword = 'Reset@123456';
  const newPassword = 'NewPass@123';

  const registered = await apiCall(request, 'POST', '/auth/register', {
    phone, password: oldPassword, confirmPassword: oldPassword,
  });
  expect(registered?.code, `注册失败：${JSON.stringify(registered)}`).toBe(0);

  await page.goto(join(USER_URL, '/forgot-password'));
  await page.getByTestId('forgot-phone').fill(phone);

  const consoleLogs: string[] = [];
  page.on('console', (m) => { if (m.type() === 'info') consoleLogs.push(m.text()); });

  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/reset/sms-code') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByTestId('forgot-send-code').click();
  const body: any = await (await resp).json();
  expect(body?.code, `发送验证码失败：${JSON.stringify(body)}`).toBe(0);
  expect(body?.data?.demoCode, 'dev 环境后端应回显 demoCode').toMatch(/^\d{6}$/);
  await expect.poll(() => consoleLogs.some((t) => t.includes(body.data.demoCode)), { timeout: 8_000 })
    .toBe(true, '浏览器控制台应能看到验证码');

  const sendBtn = page.getByTestId('forgot-send-code');
  await expect(sendBtn).toBeDisabled();
  await expect(sendBtn).toContainText('后重发');
  expect(await page.locator('.forgot-demo').innerText(), '不再展示写死的 123456').not.toContain('123456');

  // 第一步（手机号 + 验证码同行）：错误验证码必须被挡住，不能进入第二步
  const wrongCode = body.data.demoCode === '000000' ? '111111' : '000000';
  await page.getByTestId('forgot-code').fill(wrongCode);
  const verifyBadResp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/reset/verify-code') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByTestId('forgot-next').click();
  expect((await (await verifyBadResp).json())?.code, '错误验证码必须被拒').toBe(42203);
  await expect(page.getByTestId('forgot-code'), '仍停留在第一步').toBeVisible();
  await expect(page.getByTestId('forgot-new-password')).toHaveCount(0);

  // 正确验证码：第一步点「下一步」时后端只校验不消费，通过后才进入设置新密码
  await page.getByTestId('forgot-code').fill(body.data.demoCode);
  const verifyOkResp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/reset/verify-code') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByTestId('forgot-next').click();
  expect((await (await verifyOkResp).json())?.code, '正确验证码应通过校验').toBe(0);

  await expect(page.getByTestId('forgot-new-password')).toBeVisible();
  // 第二步不再重复要验证码（第一步已校验过）
  await expect(page.getByTestId('forgot-code')).toHaveCount(0);
  await page.getByTestId('forgot-new-password').fill(newPassword);
  await page.getByTestId('forgot-confirm-password').fill(newPassword);

  const resetResp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/reset/password') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByTestId('forgot-submit').click();
  expect((await (await resetResp).json())?.code).toBe(0);
  await expect(page.getByRole('heading', { name: '密码重置成功' })).toBeVisible({ timeout: 10_000 });

  const oldLogin = await apiCall(request, 'POST', '/auth/login', {
    username: phone, password: oldPassword, aud: 'user', deviceId: 'forgot-e2e-old',
  });
  expect(oldLogin?.code, '旧密码必须失效').not.toBe(0);
  const newLogin = await apiCall(request, 'POST', '/auth/login', {
    username: phone, password: newPassword, aud: 'user', deviceId: 'forgot-e2e-new',
  });
  expect(newLogin?.code, `新密码必须可登录：${JSON.stringify(newLogin)}`).toBe(0);

  // 验证码一次性消费：同一个码再次提交必须被拒
  const reuse = await apiCall(request, 'POST', '/auth/reset/password', {
    phone, code: body.data.demoCode, password: newPassword, confirmPassword: newPassword,
  });
  expect(reuse?.code).toBe(42203);
});
