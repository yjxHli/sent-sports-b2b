/**
 * 商品详情页 · 无图/单图商品的图集栅格回归（2026-11-22）
 *
 * 缺陷现场：`.detail-main` 是 `56px | minmax(0,1fr) | 420px` 三列栅格，
 * 而竖排缩略图 `.gallery-rail` 带 `v-if="galleryImages.length > 1"`。
 * 当商品只有 0~1 张图时 rail 节点不渲染，Grid 自动重排把剩下的两个子元素
 * 依次塞进前两列 → 主图区被压进 56px 缩略图轨道（实测 54×54 的小色块），
 * 购买区反而吃掉 1fr，右侧 420px 空成一列白。
 *
 * 断言口径（与数据解耦，不写死商品 id）：
 *   * 从库里挑一个「图集 ≤1 张」的在售商品（没有就跳过并说明原因）；
 *   * 主图区宽度必须撑开（≥400px），不能退化成缩略图轨道宽度；
 *   * 主图占位块必须是可见的大方块（≥200px），不是 50 来像素的碎块；
 *   * 购买区必须落在主图区右侧，且窄于主图区（420px 决策栏）。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173/';
const USER_NAME = process.env.E2E_USER_NAME ?? '13800138000';
const USER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';

function mysqlBin(): string {
  if (process.env.E2E_MYSQL_BIN) return process.env.E2E_MYSQL_BIN;
  return existsSync(LOCAL_MYSQL) ? LOCAL_MYSQL : 'mysql';
}

function sql(statement: string): string {
  return execFileSync(mysqlBin(), [
    '-h127.0.0.1', '-P3306', '-uroot', `-p${process.env.E2E_DB_PASSWORD ?? '123456'}`,
    '--default-character-set=utf8mb4',
    'sent_dev', '-N', '-B', '-e', statement,
  ], { encoding: 'utf8' }).trim();
}

/** 图集 ≤1 张（images_json 为 NULL 或空数组）的在售商品，取销量最高的一个 */
function pickProductWithoutGallery(): { id: string; title: string } | null {
  const row = sql(
    `SELECT p.id, p.title FROM product p
      WHERE p.deleted = 0 AND p.status = 'ON_SHELF'
        AND (p.images_json IS NULL OR JSON_LENGTH(p.images_json) <= 1)
      ORDER BY p.sales_count DESC, p.id ASC
      LIMIT 1;`,
  );
  if (!row) return null;
  const [id, ...rest] = row.split('\t');
  return { id, title: rest.join('\t') };
}

async function login(page: Page): Promise<void> {
  await page.goto(USER_URL, { waitUntil: 'networkidle' });
  if (await page.locator('input[type="password"]').count()) {
    await page.locator('input').first().fill(USER_NAME);
    await page.locator('input[type="password"]').fill(USER_PASSWORD);
    await page.getByRole('button', { name: '登录' }).click();
    await page.waitForLoadState('networkidle');
  }
}

test.describe('商品详情页 · 无图商品的图集栅格', () => {
  test('图集 ≤1 张时主图区必须撑开，不得被压进缩略图轨道', async ({ page }) => {
    const target = pickProductWithoutGallery();
    test.skip(!target, '库中没有「图集 ≤1 张」的在售商品，跳过（不是缺陷）');

    await login(page);
    await page.goto(`${USER_URL.replace(/\/+$/, '')}/product/${target!.id}`, { waitUntil: 'networkidle' });
    await expect(page.locator('.detail-main'), '详情页主体应渲染').toBeVisible({ timeout: 15000 });
    await expect(page.locator('.buy__title')).toContainText(target!.title.slice(0, 6));

    const box = await page.evaluate(() => {
      const r = (sel: string) => {
        const el = document.querySelector(sel);
        if (!el) return null;
        const b = el.getBoundingClientRect();
        return { x: Math.round(b.x), w: Math.round(b.width), h: Math.round(b.height) };
      };
      return {
        gallery: r('.gallery'),
        buy: r('.buy'),
        thumb: r('.gallery__main .thumb'),
        rail: r('.gallery-rail'),
      };
    });

    expect(box.rail, '图集 ≤1 张时不应渲染竖排缩略图轨道').toBeNull();
    expect(box.gallery, '主图区应存在').not.toBeNull();
    expect(box.thumb, '主图占位块应存在').not.toBeNull();

    // 核心断言：主图区不能被 Grid 自动重排塞进 56px 轨道
    expect(box.gallery!.w, `主图区宽度 ${box.gallery!.w}px 被压缩（应 ≥400px）`).toBeGreaterThanOrEqual(400);
    expect(box.thumb!.w, `主图占位块宽度 ${box.thumb!.w}px 过小（应 ≥200px）`).toBeGreaterThanOrEqual(200);

    // 反向断言：主图也不得随 1fr 无限放大（1440 视口曾撑到 890px 的巨型方块）
    expect(box.thumb!.w, `主图 ${box.thumb!.w}px 过大（上限 560px）`).toBeLessThanOrEqual(560);
    expect(box.gallery!.w, `主图区 ${box.gallery!.w}px 过宽（上限 680px）`).toBeLessThanOrEqual(680);

    // 购买区仍在主图区右侧，且是 420px 决策栏而不是 1fr 撑满
    expect(box.buy!.x, '购买区应位于主图区右侧').toBeGreaterThan(box.gallery!.x + box.gallery!.w);
    expect(box.buy!.w, `购买区宽度 ${box.buy!.w}px 撑满（应约 420px）`).toBeLessThanOrEqual(460);
  });
});
