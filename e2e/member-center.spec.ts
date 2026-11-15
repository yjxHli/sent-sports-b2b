/**
 * 会员中心（重新设计）验收（TDD 红→绿）。
 *
 * 版式：等级主视觉卡（头衔 + 成长值进度 + 有效期）→ 四项数据概览（积分/成长值/授信/应收）
 * → 等级权益 → 专属商品 → 快捷入口。
 *
 * 断言口径：
 *   1) 等级卡可见，含等级名与成长值进度条；
 *   2) 数据概览 4 项齐全且为真实数字（不是 -- / 0 占位）；
 *   3) 权益至少 3 条；
 *   4) 快捷入口至少 4 个，能跳到订单/定制/售后/客服等页面；
 *   5) 无 console error。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

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

test('会员中心：等级卡、四项概览、权益与快捷入口', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/member`);

  // 1) 等级主视觉卡
  const hero = page.locator('.member-hero');
  await expect(hero, '应有等级主视觉卡').toBeVisible({ timeout: 20_000 });
  await expect(page.locator('.member-hero__level'), '应显示等级头衔').not.toBeEmpty();
  await expect(page.locator('.member-hero__growth'), '应显示成长值').toContainText('成长值');
  await expect(page.locator('.member-hero .el-progress'), '应有成长值进度条').toBeVisible();

  // 1.1) 等级 / 差额 / 有效期口径（缺陷回归：LV1 + 成长值 16167 + 差额 -11167 + 有效期 1970-01-01）
  const TIER_ORDER = ['NORMAL', 'LV1', 'LV2', 'LV3'];
  const growthText = await page.locator('.member-hero__growth-top span').first().innerText();
  const growth = Number(growthText.replace(/[^\d]/g, '')) || 0;
  const expectedTier = growth >= 20_000 ? 'LV3' : growth >= 5_000 ? 'LV2' : growth >= 1_000 ? 'LV1' : 'NORMAL';
  const levelText = (await page.locator('.member-hero__level').innerText()).trim();
  expect(
    TIER_ORDER.indexOf(levelText),
    `成长值 ${growth} 的等级不应低于 ${expectedTier}（实际 ${levelText}）`,
  ).toBeGreaterThanOrEqual(TIER_ORDER.indexOf(expectedTier));

  const gapText = (await page.locator('.member-hero__gap').innerText()).trim();
  expect(gapText, `差额不应为负数：${gapText}`).not.toMatch(/-\d/);

  const validText = (await page.locator('.member-hero__valid').innerText()).trim();
  expect(validText, `有效期不应是 1970 纪元值：${validText}`).not.toContain('1970');

  const scaleText = (await page.locator('.member-hero__scale').innerText()).replace(/\s+/g, ' ').trim();
  expect(scaleText, `进度刻度应落在当前等级区间内：${scaleText}`).not.toContain('-');

  // 2) 四项数据概览
  const stats = page.locator('.member-stat');
  await expect(stats, '数据概览应为 4 项').toHaveCount(4);
  const statsText = (await page.locator('.member-stats').innerText()).replace(/\s+/g, ' ');
  for (const name of ['可用积分', '成长值', '授信额度', '应收余额']) {
    expect(statsText, `概览缺少「${name}」`).toContain(name);
  }
  expect(statsText, '概览不应出现占位符 --').not.toContain('--');

  // 3) 等级权益
  const benefits = page.locator('.member-benefit');
  const benefitCount = await benefits.count();
  expect(benefitCount, '等级权益至少 3 条').toBeGreaterThanOrEqual(3);

  // 4) 专属商品区（有数据则列出，没有则显示空态，两者都算通过）
  await expect(page.locator('.member-goods'), '应有专属商品区').toBeVisible();

  // 5) 快捷入口
  const links = page.locator('.member-links a');
  const linkCount = await links.count();
  expect(linkCount, '快捷入口至少 4 个').toBeGreaterThanOrEqual(4);

  // 点「我的订单」应真的跳过去
  await page.locator('.member-links a', { hasText: '我的订单' }).first().click();
  await expect(page).toHaveURL(/\/orders/, { timeout: 15_000 });

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});
