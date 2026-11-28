/**
 * 会员中心（重新设计）验收（TDD 红→绿）。
 *
 * 版式：等级主视觉卡（头衔 + 成长值进度 + 有效期）→ 四项数据概览（积分/成长值/授信/应收）
 * → 等级权益（四项一行）→ 专属商品。
 *
 * 断言口径：
 *   1) 等级卡可见，含等级名与成长值进度条；
 *   2) 数据概览 4 项齐全且为真实数字（不是 -- / 0 占位）；
 *   3) 四项权益排在同一行；
 *   4) 旧的「常用入口」整块已删除；
 *   5) 会员专属商品卡与「去逛逛」都新开标签页，原页不动；
 *   6) 无 console error。
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
  await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  await page.getByRole('button', { name: '登录' }).click();
  const body: any = await (await resp).json().catch(() => null);
  expect(body?.code, `用户端登录失败：${JSON.stringify(body)}`).toBe(0);
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

test('会员中心：等级卡、四项概览、一行权益与专属商品新标签', async ({ page }) => {
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

  // 3) 等级权益：四项必须排在同一行（2026-11-28 用户要求）
  const benefits = page.locator('.member-benefit');
  await expect(benefits, '等级权益应为四项').toHaveCount(4);
  const tops = await benefits.evaluateAll((els) => els.map((el) => el.getBoundingClientRect().top));
  expect(Math.max(...tops) - Math.min(...tops), `四项权益应一行，实际 top=${tops.join('/')}`).toBeLessThan(2);

  // 4) 旧的「常用入口」整块（含内容）已删除
  await expect(page.locator('.member-links'), '常用入口区块应删除').toHaveCount(0);
  await expect(page.getByText('常用入口', { exact: true }), '页面不应再出现「常用入口」').toHaveCount(0);

  // 5) 专属商品卡与「去逛逛」都是原生新标签锚点
  const more = page.locator('.member-goods .member-card__more');
  await expect(more, '去逛逛应是链接').toHaveCount(1);
  expect(await more.evaluate((el) => el.tagName)).toBe('A');
  await expect(more).toHaveAttribute('target', '_blank');
  await expect(more).toHaveAttribute('rel', /noopener/);

  const good = page.locator('.member-good').first();
  await expect(good, '应有专属商品卡').toBeVisible({ timeout: 20_000 });
  expect(await good.evaluate((el) => el.tagName), '商品卡应是原生锚点').toBe('A');
  await expect(good).toHaveAttribute('target', '_blank');
  await expect(good).toHaveAttribute('href', /\/product\/\w+/);

  const goodPopup = page.waitForEvent('popup', { timeout: 20_000 });
  await good.click();
  const detail = await goodPopup;
  await expect(detail).toHaveURL(/\/product\/\w+/, { timeout: 20_000 });
  await detail.close();
  expect(page.url(), '点商品卡后会员中心原页不应导航走').toContain('/member');

  const morePopup = page.waitForEvent('popup', { timeout: 20_000 });
  await more.click();
  const products = await morePopup;
  await expect(products).toHaveURL(/\/products(\?|$)/, { timeout: 20_000 });
  await products.close();
  expect(page.url(), '点去逛逛后会员中心原页不应导航走').toContain('/member');

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});
