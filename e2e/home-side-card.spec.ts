/**
 * 首页右侧用户卡验收（TDD 红→绿）。
 *
 * 背景：用户卡是 B2B 场景的差异点（会员资产 + 快捷入口 + 企业采购），
 * 但在 `@media (max-width:1240px)` 里被 `display:none` 一刀砍掉，
 * 连带「我的订单/收藏/足迹/在线客服/团购专区」六个入口全部消失；
 * 且「会员数据缺失」时整张卡没有任何登录入口。
 *
 * 本文件守三件事：
 *   1) 会员数据拿不到（token 过期/接口失败）时，卡内必须有「登录/注册」CTA；
 *   2) 登录态下四个快捷入口 + 在线客服 + 团购按钮齐全，企业采购标题不带问号；
 *   3) 窄屏（1280）用户卡不得隐藏，必须下移后仍然可见。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  const resp = page.waitForResponse(
    (r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

test('会员数据丢失时，用户卡必须给出登录 / 注册入口', async ({ page }) => {
  // 模拟「token 还在但 /api/me 拿不到会员资料」（token 过期或被服务端清掉）
  await page.route('**/api/me', (route) =>
    route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":50001}' }),
  );

  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);

  const card = page.locator('.side-card');
  await expect(card, '用户卡必须存在').toBeVisible({ timeout: 20_000 });

  const cta = card.getByRole('link', { name: /登录|注册/ });
  await expect(cta, '会员数据缺失时，用户卡内必须有「登录 / 注册」入口，不能只显示一句「未登录」').toBeVisible();
});

test('登录态用户卡：四个快捷入口齐全，企业采购标题不带问号', async ({ page }) => {
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);

  const card = page.locator('.side-card');
  await expect(card).toBeVisible({ timeout: 20_000 });

  // 四个快捷入口 + 在线客服 = 5 个链接
  await expect(card.locator('.side-links .side-link')).toHaveCount(5);
  for (const label of ['我的订单', '我的定制', '我的收藏', '我的足迹', '在线客服']) {
    await expect(
      card.locator('.side-link', { hasText: label }).first(),
      `快捷入口「${label}」必须存在`,
    ).toBeVisible();
  }

  // 企业采购不该用疑问语气弱化（B2B 主营场景）
  await expect(card.locator('.side-tip b'), '企业采购标题不应带问号').toHaveText('企业采购');

  // 团购入口保留实心 CTA 语义（文案不变，避免其它用例失效）
  await expect(card.locator('.side-tip__btn')).toHaveText(/查看团购/);
});

test('窄屏 1280 下用户卡不得隐藏，应下移后仍可见', async ({ page }) => {
  await page.setViewportSize({ width: 1280, height: 900 });
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);

  const card = page.locator('.side-card');
  await expect(card, '1280 视口下用户卡被 display:none 隐藏了，六个入口随之消失').toBeVisible({
    timeout: 20_000,
  });

  // 下移判据：卡片顶部应落在中栏底部之下（允许 12px 误差）
  const mainBox = await page.locator('.home-main').boundingBox();
  const cardBox = await card.boundingBox();
  expect(mainBox && cardBox, '应能取到中栏与用户卡的位置').toBeTruthy();
  expect(
    cardBox!.y,
    `用户卡应下移到中栏下方（中栏底部 ${Math.round(mainBox!.y + mainBox!.height)}，卡顶 ${Math.round(cardBox!.y)}）`,
  ).toBeGreaterThanOrEqual(mainBox!.y + mainBox!.height - 12);
});
