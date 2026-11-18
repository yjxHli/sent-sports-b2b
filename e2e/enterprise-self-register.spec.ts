/**
 * 企业自助注册 → 管理端审核 → 企业登录（2026-11-18 用户要求）。
 * 短信验证码暂不开放；客户号用户不可见，只通过管理端列表定位后审核。
 */
import path from 'node:path';
import { test, expect } from '@playwright/test';
import {
  USER_URL, ADMIN_URL, join, apiCall, loginApi, ADMIN_NAME, ADMIN_PASSWORD,
} from './chat-helpers';

const PHONE = `139${String(Date.now()).slice(-8)}`;
const PASSWORD = 'Ent@123456';
const CREDIT_CODE = '91310000MA1FL0AB1X';
const COMPANY = '森特E2E测试企业';

test('企业注册后待审核不可登录，管理端通过后可登录', async ({ page, request }) => {
  test.setTimeout(180_000);

  await page.goto(join(USER_URL, '/enterprise/login'));
  await expect(page.getByTestId('enterprise-sms-tab')).toBeDisabled();
  await expect(page.getByText('13900009101')).toBeVisible();
  await page.getByTestId('enterprise-register-link').click();
  await expect(page).toHaveURL(/\/enterprise\/register(\?|$)/, { timeout: 10_000 });

  await page.getByTestId('enterprise-license-input')
    .setInputFiles(path.join(__dirname, 'fixtures', 'proof.png'));
  await expect(page.locator('.el-message', { hasText: '营业执照已上传' }))
    .toBeVisible({ timeout: 15_000 });
  await page.getByTestId('enterprise-company').fill(COMPANY);
  await page.getByTestId('enterprise-credit-code').fill(CREDIT_CODE);
  await page.getByTestId('enterprise-phone').fill(PHONE);
  await page.getByTestId('enterprise-send-code').click();
  await expect(page.locator('.el-message', { hasText: '短信验证码暂未开放' }))
    .toBeVisible({ timeout: 8_000 });
  await page.getByTestId('enterprise-password').fill(PASSWORD);
  await page.getByTestId('enterprise-confirm-password').fill(PASSWORD);
  await page.getByTestId('enterprise-agree').click();

  const registered = page.waitForResponse(
    (r) => /\/api\/auth\/enterprise\/register$/.test(new URL(r.url()).pathname)
      && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByTestId('enterprise-register-submit').click();
  const body: any = await (await registered).json().catch(() => null);
  expect(body?.code, `企业注册失败：${JSON.stringify(body)}`).toBe(0);
  expect(body?.data?.auditStatus).toBe('PENDING');
  await expect(page.getByTestId('enterprise-register-success'))
    .toContainText('等待管理端审核', { timeout: 10_000 });

  // 注册后未审核：企业登录必须被业务门禁拦住
  const pending = await apiCall(request, 'POST', '/auth/login', {
    username: PHONE, password: PASSWORD, aud: 'user', deviceId: `e2e-ent-pending-${Date.now()}`,
  });
  expect(pending?.code, 'PENDING 企业不应签发令牌').toBe(42201);
  expect(String(pending?.message ?? '')).toContain('审核');

  // 客户号用户不可见：只在管理端列表里定位，再走既有审核端点
  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
  const listed = await apiCall(request, 'GET',
    `/admin/customers?page=1&size=20&type=ENTERPRISE&keyword=${PHONE}`, undefined, adminToken);
  const row = (listed?.data?.list ?? []).find((r: any) => r.phone === PHONE);
  expect(row, `管理端应能找到新注册企业：${JSON.stringify(listed)}`).toBeTruthy();
  const customerId = Number(row.customerId);
  expect(customerId).toBeGreaterThan(0);
  // 审核材料必须随列表下发，否则管理端只能「盲审」
  expect(row.companyName, '列表应带出企业名称').toBe(COMPANY);
  expect(row.licenseNo, '列表应带出统一社会信用代码').toBe(CREDIT_CODE);
  expect(String(row.licenseUrl ?? ''), '列表应带出营业执照 fileId').not.toBe('');

  // 管理端审核页要能直接看到提交的材料（企业名称 / 信用代码 / 营业执照图）
  await page.goto(join(ADMIN_URL, '/login'));
  await page.getByPlaceholder('用户名').fill(ADMIN_NAME);
  await page.getByPlaceholder('密码').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/dashboard/, { timeout: 20_000 });
  await page.goto(join(ADMIN_URL, '/customer/enterprise'));
  await page.getByPlaceholder('企业名称 / 企业 ID').fill(PHONE);
  await page.getByRole('button', { name: '查询' }).click();
  const entRow = page.locator('tbody tr', { hasText: COMPANY }).first();
  await expect(entRow, '审核页应按企业名称搜到刚注册的企业').toBeVisible({ timeout: 20_000 });
  await entRow.getByRole('button', { name: '选择' }).click();
  await expect(page.locator('.el-descriptions')).toContainText(COMPANY, { timeout: 10_000 });
  await expect(page.locator('.el-descriptions')).toContainText(CREDIT_CODE);
  await expect(page.getByTestId('enterprise-license-img'), '营业执照缩略图应可预览')
    .toBeVisible({ timeout: 10_000 });

  const audited = await apiCall(request, 'POST', `/admin/enterprise/${customerId}/audit`,
    { approved: true, remark: 'E2E 审核通过' }, adminToken);
  expect(audited?.code, `审核失败：${JSON.stringify(audited)}`).toBe(0);
  expect(audited?.data?.status).toBe('ACTIVE');

  const approved = await apiCall(request, 'POST', '/auth/login', {
    username: PHONE, password: PASSWORD, aud: 'user', deviceId: `e2e-ent-ok-${Date.now()}`,
  });
  expect(approved?.code, `审核通过后应能登录：${JSON.stringify(approved)}`).toBe(0);

  await page.goto(join(USER_URL, '/enterprise/login'));
  await page.getByTestId('enterprise-account').fill(PHONE);
  await page.getByTestId('enterprise-password').fill(PASSWORD);
  await page.getByTestId('enterprise-login-submit').click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
});
