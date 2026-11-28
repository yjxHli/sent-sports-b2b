/**
 * 评价中心改版验收（2026-11-28，TDD 红→绿）。
 *
 * 用户要求（对齐京东「我的评价」）：
 *   1) 顶部用户条带四项统计（已写评论 / 浏览数 / 获赞数 / 待评价）；
 *   2) 左侧「待评价 / 已评价」两个页签，默认落在待评价；
 *   3) 右侧「综合 / 最新 / 最早」排序，排序必须**下推服务端**（sort 参数），
 *      不是在前端把已加载的几条重排 —— 否则翻页顺序会错乱；
 *   4) 自己写的评价可以删除（按钮只出现在已评价行）。
 *
 * 只读用例：不写库、不删评价（删除动作由后端单测/ReviewIT/ReviewApiTest 覆盖），
 * 因此放在默认 parallel 桶。
 */
import { test, expect, type Page } from '@playwright/test';
import { USER_URL, join, CUSTOMER_PHONE, CUSTOMER_PASSWORD } from './chat-helpers';

async function login(page: Page): Promise<void> {
  await page.goto(join(USER_URL, '/login'));
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  // 2026-11-28：登录页新增协议勾选（默认未勾选），E2E 必须先确认协议
  if (await page.getByTestId('auth-agree').count()) {
    await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  }
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

test('评价中心：双页签、四项统计与排序下推服务端', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !/Failed to load resource|net::ERR/i.test(m.text())) {
      errors.push(m.text());
    }
  });
  const reviewCalls: string[] = [];
  page.on('request', (r) => {
    if (r.method() === 'GET' && r.url().includes('/api/me/reviews')) reviewCalls.push(r.url());
  });

  await login(page);
  await page.goto(join(USER_URL, '/mine/reviews'));

  // 1) 两个页签 + 四项统计
  await expect(page.getByTestId('review-tab-pending')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId('review-tab-written')).toBeVisible();
  for (const key of ['written', 'views', 'likes', 'pending']) {
    await expect(page.getByTestId(`review-stat-${key}`), `缺少统计项 ${key}`).toBeVisible();
  }
  await expect(page.getByTestId('review-stat-written')).toContainText('已写评论');
  await expect(page.getByTestId('review-stat-pending')).toContainText('待评价');

  // 2) 默认落在「待评价」：有行或空态
  await expect(page.getByTestId('review-tab-pending')).toHaveAttribute('aria-selected', 'true');
  await expect(
    page.getByTestId('pending-review-item').first().or(page.getByTestId('reviews-empty')),
    '待评价页签要有列表或空态',
  ).toBeVisible({ timeout: 20_000 });

  // 3) 进页面时已按默认排序拉过「已评价」（综合 = comprehensive）
  await expect.poll(
    () => reviewCalls.some((u) => u.includes('/api/me/reviews?') && u.includes('sort=comprehensive')),
    { timeout: 20_000, message: '已评价列表应按默认综合排序请求一次' },
  ).toBe(true);

  // 4) 切到已评价 → 改成「最早」→ 请求必须带 sort=earliest（服务端排序）
  await page.getByTestId('review-tab-written').click();
  await expect(page.getByTestId('review-sort-earliest')).toBeVisible({ timeout: 15_000 });
  await page.getByTestId('review-sort-earliest').click();
  await expect.poll(
    () => reviewCalls.some((u) => u.includes('sort=earliest')),
    { timeout: 15_000, message: '点「最早」应重新请求并带 sort=earliest' },
  ).toBe(true);

  await page.getByTestId('review-sort-latest').click();
  await expect.poll(
    () => reviewCalls.some((u) => u.includes('sort=latest')),
    { timeout: 15_000, message: '点「最新」应重新请求并带 sort=latest' },
  ).toBe(true);

  // 5) 已评价列表：有行或空态；有行时必须带「删除」入口
  const writtenRows = page.getByTestId('review-item');
  await expect(
    writtenRows.first().or(page.getByTestId('reviews-empty')),
    '已评价页签要有列表或空态',
  ).toBeVisible({ timeout: 20_000 });
  if (await writtenRows.count()) {
    await expect(writtenRows.first().getByTestId('review-delete'), '自己的评价应有删除入口')
      .toBeVisible();
  }

  expect(errors, `评价中心不应有 console error：${errors.join(' | ')}`).toHaveLength(0);
});
