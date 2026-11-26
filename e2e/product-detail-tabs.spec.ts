/**
 * 商品详情页「商品详情 / 用户评价」真切换 + 好评差评计数（2026-11-22）。
 *
 * 用户诉求：
 *   1) 选「商品详情」时只能看到商品详情内容（描述/图集/参数/服务），不能和评价叠在一起；
 *   2) 选「用户评价」时只看到评价内容；
 *   3) 评价头部要能看到「好评多少条 / 差评多少条」（口碑概览，像主流电商那样）。
 *
 * 断言链（真实读写，不 mock）：
 *   - 先按 SQL 读出该商品原有好评/差评基线与总数（演示库里可能有别的用例留下的评价）；
 *   - 用 SQL 播一条 5 星 + 一条 1 星评价（独立 order_no，用例结束按 order_no 精确删除）；
 *   - 详情页默认落在「商品详情」：详情面板可见、评价面板隐藏，评价卡片不参与首屏；
 *   - 切到「用户评价」：评价面板可见、详情面板隐藏，好评 = 基线+1、差评 = 基线+1；
 *   - 切回「商品详情」：面板再翻转一次，证明不是一次性样式残留。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';
import { pickSellableSku } from './pick-product';

const USER_URL = (process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const USER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const USER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';
const STAMP = Date.now().toString().slice(-6);
const GOOD_ORDER_NO = `PDG${STAMP}`;
const BAD_ORDER_NO = `PDB${STAMP}`;
const CUSTOMER_ID = 9002;

function mysqlBin(): string {
  if (process.env.E2E_MYSQL_BIN) return process.env.E2E_MYSQL_BIN;
  return existsSync(LOCAL_MYSQL) ? LOCAL_MYSQL : 'mysql';
}

function sql(statement: string): string {
  return execFileSync(mysqlBin(), [
    '-h127.0.0.1', '-P3306', '-uroot', `-p${process.env.E2E_DB_PASSWORD ?? '123456'}`,
    'sent_dev', '-N', '-B', '-e', statement,
  ], { encoding: 'utf8' }).trim();
}

interface Baseline {
  positive: number;
  negative: number;
}

/** 基线：库里已有多少好评（rating>=4）/差评（rating<=2），避免和演示数据抢断言 */
function baseline(productId: string): Baseline {
  const out = sql(`SELECT COALESCE(SUM(rating >= 4), 0), COALESCE(SUM(rating <= 2), 0) `
    + `FROM product_review WHERE product_id = ${Number(productId)}`);
  const [positive, negative] = out.split('\t');
  return { positive: Number(positive), negative: Number(negative) };
}

function seedReview(orderNo: string, productId: string, skuId: string, rating: number, content: string): void {
  sql(`INSERT INTO product_review (product_id, sku_id, order_no, customer_id, rating, content, images_json) `
    + `VALUES (${Number(productId)}, ${Number(skuId)}, '${orderNo}', ${CUSTOMER_ID}, ${rating}, '${content}', '[]')`);
}

function cleanup(): void {
  sql(`DELETE FROM product_review WHERE order_no IN ('${GOOD_ORDER_NO}', '${BAD_ORDER_NO}')`);
}

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(USER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(USER_PASSWORD);
  await page.getByRole('button', { name: '登录' }).first().click();
  await expect(page, '未登录成功，详情页会被路由守卫拦到登录页').not.toHaveURL(/\/login/, { timeout: 20_000 });
}

async function openProduct(page: Page, productId: string): Promise<void> {
  await page.goto(`${USER_URL}/product/${productId}`);
  await expect(page.locator('h1.buy__title'), '详情页应加载出商品主信息').toBeVisible({ timeout: 20_000 });
}

test('商品详情与用户评价互斥显示，评价头部给出好评/差评条数', async ({ page }) => {
  test.setTimeout(120_000);
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !/Failed to load resource|net::ERR/i.test(m.text())) errors.push(m.text());
  });

  const picked = await pickSellableSku(page.request, 1);
  const base = baseline(picked.productId);
  seedReview(GOOD_ORDER_NO, picked.productId, picked.skuId, 5, `E2E 好评 ${STAMP}`);
  seedReview(BAD_ORDER_NO, picked.productId, picked.skuId, 1, `E2E 差评 ${STAMP}`);

  try {
    await loginUser(page);
    await openProduct(page, picked.productId);

    const descPanel = page.locator('[data-testid="detail-panel-desc"]');
    const reviewPanel = page.locator('[data-testid="detail-panel-review"]');
    const descTab = page.locator('[data-testid="detail-tab-desc"]');
    const reviewTab = page.locator('[data-testid="detail-tab-review"]');

    // 1) 默认：只有商品详情，评价不在视口里
    await expect(descTab).toHaveClass(/active/);
    await expect(descPanel).toBeVisible();
    await expect(reviewPanel, '未切到「用户评价」时评价内容不得和商品详情叠在一起').toBeHidden();

    // 2) 切到用户评价：详情整块收起，评价独占
    await reviewTab.click();
    await expect(reviewTab).toHaveClass(/active/);
    await expect(descTab).not.toHaveClass(/active/);
    await expect(reviewPanel).toBeVisible();
    await expect(descPanel, '选「用户评价」后不应还看到商品详情').toBeHidden();

    // 3) 好评/差评条数 = 基线 + 本次播种（5 星算好评、1 星算差评）
    const good = page.locator('[data-testid="review-positive-count"]');
    const bad = page.locator('[data-testid="review-negative-count"]');
    await expect(good, '评价头部应有好评条数').toHaveText(String(base.positive + 1), { timeout: 20_000 });
    await expect(bad, '评价头部应有差评条数').toHaveText(String(base.negative + 1));
    await expect(reviewPanel).toContainText(`E2E 好评 ${STAMP}`);
    await expect(reviewPanel).toContainText(`E2E 差评 ${STAMP}`);

    // 4) 切回商品详情：面板再翻转，评价内容必须撤出
    await descTab.click();
    await expect(descPanel).toBeVisible();
    await expect(reviewPanel).toBeHidden();

    expect(errors, `控制台不应有业务错误：${errors.join(' | ')}`).toEqual([]);
  } finally {
    cleanup();
  }
});

test('无评价商品：好评/差评都显示 0 条，不出现 null/NaN', async ({ page }) => {
  test.setTimeout(120_000);
  const picked = await pickSellableSku(page.request, 1);
  // 选一个当前没有任何评价的商品（演示库大多商品没评价；找不到就跳过，避免误判功能）
  const count = sql(`SELECT COUNT(*) FROM product_review WHERE product_id = ${Number(picked.productId)}`);
  test.skip(Number(count) > 0, '挑中的商品已有评价，无法验证空态；换一个商品再跑');

  await loginUser(page);
  await openProduct(page, picked.productId);
  await page.locator('[data-testid="detail-tab-review"]').click();

  const good = page.locator('[data-testid="review-positive-count"]');
  const bad = page.locator('[data-testid="review-negative-count"]');
  await expect(good).toHaveText('0');
  await expect(bad).toHaveText('0');
});
