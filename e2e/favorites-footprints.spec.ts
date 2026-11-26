/**
 * 商品收藏 + 收藏夹 + 个人足迹 + 个人中心售后角标（2026-11-16，TDD 红→绿）。
 *
 * 用户诉求（第 4 轮）：
 *   1) 商品详情没有收藏功能 → 详情页加心形收藏按钮；
 *   2) 顶部导航最前面加「首页」、中间加「收藏夹」（存放收藏商品）；
 *   3) 个人中心「个人足迹」无内容、退款/售后显示 0（实际有退款单）；
 *   4) 会员专属商品无内容（由 member-center.spec 覆盖）。
 *
 * 断言口径：
 *   - 顶栏第一个链接是「首页」（href /home），且存在「收藏夹」（href /favorites）；
 *   - 详情页点收藏 → 按钮文案变「已收藏」→ 收藏夹里出现该商品；
 *   - 收藏夹里取消收藏 → 卡片数量减一；
 *   - 个人中心：收藏卡与足迹卡都渲染出真实商品缩略图，退款/售后角标 > 0。
 *
 * 数据准备：跑前把 9002 的收藏 / 足迹重置成确定的两条，避免上一轮用例残留导致数量断言漂移。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';

const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

const CUSTOMER_ID = 9002;
/** 在售商品：9101 斯伯丁篮球 / 9102 训练器材（详情页可直接收藏） */
const FAV_SEED_PRODUCT = 9101;
const FAV_NEW_PRODUCT = 9102;
const FOOTPRINT_SEED_PRODUCT = 9107;

const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';

function resolveMysqlBin(): string {
  const fromEnv = process.env.E2E_MYSQL_BIN;
  if (fromEnv) return fromEnv;
  return existsSync(LOCAL_MYSQL) ? LOCAL_MYSQL : 'mysql';
}

function runSql(sql: string): void {
  const mysql = resolveMysqlBin();
  const password = process.env.E2E_DB_PASSWORD ?? '123456';
  execFileSync(
    mysql,
    ['-h127.0.0.1', '-P3306', '-uroot', `-p${password}`, 'sent_dev', '-e', sql],
    { stdio: 'ignore' },
  );
}

/** 收藏 1 条 + 足迹 1 条：数量断言才有确定基准 */
function resetMarks(): void {
  runSql(`DELETE FROM product_favorite WHERE customer_id = ${CUSTOMER_ID};
DELETE FROM product_footprint WHERE customer_id = ${CUSTOMER_ID};
INSERT INTO product_favorite (customer_id, product_id, created_by) VALUES (${CUSTOMER_ID}, ${FAV_SEED_PRODUCT}, ${CUSTOMER_ID});
INSERT INTO product_footprint (customer_id, product_id, view_count, last_viewed_at, created_by)
VALUES (${CUSTOMER_ID}, ${FOOTPRINT_SEED_PRODUCT}, 2, NOW(3), ${CUSTOMER_ID});`);
}

const pathOf = (page: Page): string => new URL(page.url()).pathname;

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

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

test('顶栏有首页与收藏夹，商品可收藏并在收藏夹管理中生效', async ({ page }) => {
  test.setTimeout(180_000);
  const consoleErrors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) consoleErrors.push(m.text());
  });

  resetMarks();
  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/home`);
  await expect(page.locator('.topbar'), '顶栏应渲染完成').toBeVisible({ timeout: 20_000 });

  // 1) 顶栏：第一个入口必须是「首页」，且存在「收藏夹」
  const links = page.locator('.topbar__links .topbar__link');
  await expect(links.first(), '顶栏最前面应是「首页」').toHaveText('首页');
  await expect(links.first(), '「首页」应指向 /home').toHaveAttribute('href', /\/home$/);
  const favLink = page.locator('.topbar__links .topbar__link', { hasText: '收藏夹' }).first();
  await expect(favLink, '顶栏应有「收藏夹」入口').toBeVisible();
  await expect(favLink, '「收藏夹」应指向 /favorites').toHaveAttribute('href', /\/favorites$/);

  // 2) 详情页收藏：点一下变「已收藏」
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/product/${FAV_NEW_PRODUCT}`);
  const favBtn = page.locator('.buy-fav');
  await expect(favBtn, '商品详情应有收藏按钮').toBeVisible({ timeout: 20_000 });
  await expect(favBtn, '进页面时该商品应未收藏').toContainText('收藏');

  const addResp = page.waitForResponse(
    (r) => r.url().includes(`/api/favorites/${FAV_NEW_PRODUCT}`) && r.request().method() === 'POST',
    { timeout: 20_000 },
  );
  await favBtn.click();
  const added: any = await (await addResp).json().catch(() => null);
  expect(added?.code, `收藏失败：${JSON.stringify(added)}`).toBe(0);
  await expect(favBtn, '收藏成功后按钮应变成「已收藏」').toContainText('已收藏');

  // 3) 收藏夹：种子的 1 条 + 刚收藏的 1 条
  await favLink.click();
  await expect.poll(() => pathOf(page), { timeout: 20_000 }).toBe('/favorites');
  const cards = page.locator('.fav-card');
  await expect(cards, '收藏夹应有 2 件商品').toHaveCount(2, { timeout: 20_000 });
  await expect(page.locator('.fav-bar__count'), '标题旁应显示总件数').toContainText('共 2 件');

  // 4) 取消收藏：点第一张卡片的心形，数量回到 1
  const removeResp = page.waitForResponse(
    (r) => r.url().includes('/api/favorites/') && r.request().method() === 'DELETE',
    { timeout: 20_000 },
  );
  await cards.first().locator('.fav-card__heart').click();
  const removed: any = await (await removeResp).json().catch(() => null);
  expect(removed?.code, `取消收藏失败：${JSON.stringify(removed)}`).toBe(0);
  await expect(cards, '取消收藏后应只剩 1 件').toHaveCount(1, { timeout: 20_000 });

  expect(consoleErrors, `console errors: ${consoleErrors.join(' | ')}`).toHaveLength(0);
});

test('个人中心：收藏卡与足迹卡有真实商品，退款/售后角标不再是 0', async ({ page }) => {
  test.setTimeout(120_000);
  const consoleErrors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) consoleErrors.push(m.text());
  });

  resetMarks();
  await loginUser(page);

  // 先看一件商品，让足迹 +1（详情页静默上报）
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/product/${FAV_SEED_PRODUCT}`);
  await expect(page.locator('.buy-fav'), '商品详情应加载完成').toBeVisible({ timeout: 20_000 });
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/mine`);
  await expect(page.locator('.mine-page'), '个人中心应渲染').toBeVisible({ timeout: 20_000 });

  // 1) 收藏卡：有一条种子收藏 → 渲染出迷你商品条
  const favPanel = page.locator('.mini-panel').filter({ hasText: '我的收藏' });
  await expect(favPanel.locator('.mini-item'), '收藏卡应展示真实收藏商品').toHaveCount(1, {
    timeout: 20_000,
  });
  await expect(favPanel.locator('.panel-more'), '收藏卡应给出「全部 N 件」入口').toContainText('全部 1 件');

  // 2) 足迹卡：刚才浏览的商品会进足迹（种子 1 条 + 新浏览 1 件 = 2）
  const fpPanel = page.locator('.mini-panel').filter({ hasText: '我的足迹' });
  await expect(fpPanel.locator('.mini-item'), '足迹卡应展示浏览过的商品').toHaveCount(2, {
    timeout: 20_000,
  });

  // 3) 退款/售后角标：有真实退款单时必须 > 0（旧实现用 Boolean(refundStatus) 恒为 0）
  const refundStat = page.locator('.order-stat').filter({ hasText: '退款/售后' });
  await expect(refundStat, '订单区应有「退款/售后」统计').toBeVisible();
  await expect.poll(async () => Number((await refundStat.locator('b').innerText()).trim()), {
    timeout: 20_000,
    message: '退款/售后数量应大于 0（演示客户有多笔售后单）',
  }).toBeGreaterThan(0);

  // 4) 点「退款/售后」进的是售后列表，不是订单列表
  await refundStat.click();
  await expect.poll(() => pathOf(page), { timeout: 20_000 }).toBe('/refunds');

  expect(consoleErrors, `console errors: ${consoleErrors.join(' | ')}`).toHaveLength(0);
});
