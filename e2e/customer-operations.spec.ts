import { test, expect, type Page } from '@playwright/test';

/**
 * 客户与会员 · 客户运营动作验收（TDD 红→绿）。
 *
 * 用户缺陷：客户列表「改派」和详情页「授予等级 / 积分调整 / 改派归属 / 刷新」
 * 会弹出「系统问题」，控制台出现 3 条
 * `Failed to load resource: the server responded with a status of 404 (Not Found)`。
 *
 * 这里逐动作断言：请求 HTTP 200 + 业务 code=0，且全程无 4xx/5xx、无 console error。
 */
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
/** 取列表里稳定的种子客户（真库 customer 表）。 */
const DETAIL = ADMIN_URL + 'customer/detail/91203';

interface Call {
  status: number;
  code: number | null;
  url: string;
}

async function login(page: Page): Promise<void> {
  await page.goto(ADMIN_URL + 'login');
  await page.waitForTimeout(900);
  if (page.url().includes('login')) {
    await page.getByPlaceholder('用户名').fill('admin');
    await page.getByPlaceholder('密码').fill('123456');
    await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
      ),
      page.getByRole('button', { name: /登\s*录/ }).click(),
    ]);
  }
}

test('客户详情四个运营动作与列表改派全部成功，且无 404 / 系统异常', async ({ page }) => {
  const calls: Call[] = [];
  const badResponses: string[] = [];
  const errors: string[] = [];

  page.on('response', async (r) => {
    if (r.status() >= 400) {
      badResponses.push(`${r.status()} ${r.request().method()} ${r.url()}`);
    }
    const u = r.url();
    if (!u.includes('/api/admin/customers') || r.request().method() !== 'POST') return;
    let code: number | null = null;
    try {
      const body = JSON.parse(await r.text());
      code = typeof body.code === 'number' ? body.code : null;
    } catch { /* ignore */ }
    calls.push({ status: r.status(), code, url: u.replace(ADMIN_URL, '') });
  });
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });

  await login(page);

  // 1) 列表「改派」
  await page.goto(ADMIN_URL + 'customer/list');
  await page.waitForSelector('.el-table__body tbody tr');
  await page.waitForTimeout(900);
  await page.getByRole('button', { name: '改派', exact: true }).first().click();
  await page.waitForTimeout(300);
  await page.locator('.el-dialog:visible textarea').first().fill('E2E 回归改派');
  await page.locator('.el-dialog:visible').getByRole('button', { name: '确认改派' }).click();
  await page.waitForTimeout(1200);

  // 2) 详情「刷新」
  await page.goto(DETAIL);
  await page.waitForTimeout(900);
  await expect(page.locator('.customer-id')).toHaveText('91203');
  await page.getByRole('button', { name: '刷新' }).click();
  await page.waitForTimeout(900);
  await expect(page.locator('.customer-id')).toHaveText('91203');

  // 3) 详情「积分调整」
  await page.goto(DETAIL);
  await page.waitForTimeout(900);
  await page.getByRole('button', { name: '积分调整' }).click();
  await page.waitForTimeout(300);
  await page.locator('.el-dialog:visible .el-input-number input').first().fill('1');
  await page.locator('.el-dialog:visible textarea').first().fill('E2E 回归积分调整');
  await page.locator('.el-dialog:visible').getByRole('button', { name: '确认调整' }).click();
  await page.waitForTimeout(1200);

  // 4) 详情「改派归属」
  await page.goto(DETAIL);
  await page.waitForTimeout(900);
  await page.getByRole('button', { name: '改派归属' }).click();
  await page.waitForTimeout(300);
  await page.locator('.el-dialog:visible textarea').first().fill('E2E 回归详情改派');
  await page.locator('.el-dialog:visible').getByRole('button', { name: '确认改派' }).click();
  await page.waitForTimeout(1200);

  // 5) 详情「授予等级」（用弹窗默认等级，避免与等级码值域耦合）
  await page.goto(DETAIL);
  await page.waitForTimeout(900);
  await page.getByRole('button', { name: '授予等级' }).click();
  await page.waitForTimeout(300);
  await page.locator('.el-dialog:visible textarea').first().fill('E2E 回归授级');
  const dateInput = page.locator('.el-dialog:visible .el-date-editor input').first();
  await dateInput.fill('2027-11-10 12:00:00');
  await dateInput.press('Enter');
  await page.waitForTimeout(300);
  await page.locator('.el-dialog:visible').getByRole('button', { name: '确认授级' }).click();
  await page.waitForTimeout(1200);

  console.log('写操作响应:', JSON.stringify(calls));
  // 改派(列表) + 积分调整 + 改派(详情) + 授级 = 4 个写请求
  expect(calls, '四个运营动作都应发出 POST 请求').toHaveLength(4);
  for (const c of calls) {
    expect(c.status, `${c.url} HTTP 状态`).toBe(200);
    expect(c.code, `${c.url} 业务码`).toBe(0);
  }
  expect(badResponses, `不应出现 4xx/5xx: ${badResponses.join(' | ')}`).toHaveLength(0);
  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});
