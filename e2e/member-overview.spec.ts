import { test, expect, type Page } from '@playwright/test';

/**
 * 会员与积分验收（TDD 红→绿）。
 *
 * 用户缺陷：「会员与积分顶部无数据内容，如何确认我当前查看的是哪一个账号」。
 * 根因是 `/member/level|points|credit` 三个读接口恒抛 40401，页面三张卡永远是 `--`，
 * 且页面没有任何「当前账号」标识。
 *
 * 这里断言：
 * 1) 首屏自动锁定一个真实客户，顶部明确显示昵称 / 手机号 / 客户 ID；
 * 2) 三张卡都是真数据（不是 `--`），等级为 NORMAL/LV1/LV2/LV3 之一；
 * 3) 切换账号后卡片重新取数，且请求带上对应的 customerId。
 */
const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/';
/** 菜单路径就是 `/member/index`（MENU_ROUTES 与路由表共用），写 `/member` 会落到 404 页。 */
const MEMBER = ADMIN_URL + 'member/index';

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

test('会员与积分首屏有真实数据，并明确显示当前查看账号', async ({ page }) => {
  const errors: string[] = [];
  const memberUrls: string[] = [];
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()); });
  page.on('request', (r) => {
    if (r.method() === 'GET' && r.url().includes('/api/member/')) memberUrls.push(r.url());
  });

  await login(page);
  await page.goto(MEMBER);
  await page.waitForTimeout(2500);

  // 1) 当前账号标识：昵称 + 手机号 + 客户 ID 都要出现
  const accountBar = page.locator('.account-bar');
  await expect(accountBar).toBeVisible();
  await expect(accountBar).toContainText('当前查看账号');
  const barText = (await accountBar.innerText()).replace(/\s+/g, ' ');
  console.log('当前账号:', barText);
  expect(barText).toMatch(/客户 ID\s*\d+/);

  // 2) 三张卡都是真数据
  const metrics = page.locator('.metric');
  await expect(metrics).toHaveCount(3);
  const levelText = (await metrics.nth(0).innerText()).replace(/\s+/g, ' ');
  const pointText = (await metrics.nth(1).innerText()).replace(/\s+/g, ' ');
  const creditText = (await metrics.nth(2).innerText()).replace(/\s+/g, ' ');
  console.log('三卡:', levelText, '|', pointText, '|', creditText);

  await expect(metrics.nth(0).locator('.metric-value')).not.toHaveText('--');
  await expect(metrics.nth(1).locator('.metric-value')).not.toHaveText('--');
  expect(levelText).toMatch(/普通会员|一级会员|二级会员|三级会员/);
  expect(pointText).toMatch(/\d/);
  expect(creditText).toMatch(/¥/);

  // 3) 请求必须带 customerId，否则后端只能按当前登录者查，管理端必然错位
  expect(memberUrls.length).toBeGreaterThan(0);
  expect(memberUrls.every((u) => u.includes('customerId='))).toBeTruthy();
  console.log('会员接口:', memberUrls.slice(0, 3).join(' , '));

  // 4) 四个接口全部 200，不允许再出现 40401
  const failed: string[] = [];
  page.on('response', async (r) => {
    if (!r.url().includes('/api/member/')) return;
    try {
      const body = JSON.parse(await r.text());
      if (body.code !== 0) failed.push(`${r.url()} -> ${body.code}`);
    } catch { /* ignore */ }
  });
  await page.locator('.account-bar').getByRole('button', { name: '刷新' }).click();
  await page.waitForTimeout(1500);
  expect(failed, '会员接口不应返回非 0 业务码').toHaveLength(0);

  expect(errors, 'console 不应有错误').toHaveLength(0);
});

test('切换当前账号后卡片重新取数', async ({ page }) => {
  await login(page);
  await page.goto(MEMBER);
  await page.waitForTimeout(2500);

  const accountBar = page.locator('.account-bar');
  const before = (await accountBar.innerText()).replace(/\s+/g, ' ');
  const beforeId = Number((before.match(/客户 ID\s*(\d+)/) ?? [])[1]);
  expect(Number.isFinite(beforeId)).toBeTruthy();

  // 打开下拉并选第二个客户
  await accountBar.locator('.el-select').click();
  await page.waitForTimeout(600);
  const options = page.locator('.el-select-dropdown:visible .el-select-dropdown__item');
  const count = await options.count();
  expect(count).toBeGreaterThan(1);
  await options.nth(1).click();
  await page.waitForTimeout(2000);

  const after = (await accountBar.innerText()).replace(/\s+/g, ' ');
  const afterId = Number((after.match(/客户 ID\s*(\d+)/) ?? [])[1]);
  console.log('切换账号:', beforeId, '→', afterId);
  expect(afterId).not.toBe(beforeId);

  // 卡片仍然是真数据，不因换人回到 `--`
  const metrics = page.locator('.metric');
  await expect(metrics.nth(0).locator('.metric-value')).not.toHaveText('--');
  await expect(metrics.nth(1).locator('.metric-value')).not.toHaveText('--');
});
