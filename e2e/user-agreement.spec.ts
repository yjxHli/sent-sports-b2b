/**
 * 商城端登录页协议可读（2026-11-18 用户需求 1）。
 *
 * 用户口径：登录界面的《森特商城用户服务协议》和《隐私权政策》要「真的可以点击并阅读」，
 * 而不是勾选框里的一段死文字。
 *
 * 断言口径：
 *   1) 两段书名号文字都是链接（a 标签），可点击；
 *   2) 点开后弹窗里是该协议的标题与分节正文（服务协议含「协议的范围」、
 *      隐私政策含「我们收集哪些信息」），正文区可滚动（内容超出一屏）；
 *   3) 点协议不会误改「已阅读并同意」勾选状态，也不会离开登录页；
 *   4) 全程无 console error。
 */
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

async function openLogin(page: Page): Promise<void> {
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/login`);
  await expect(page.getByTestId('agreement-service'), '服务协议应是可点链接')
    .toBeVisible({ timeout: 20_000 });
}

test('登录页《用户服务协议》可点击并阅读全文', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await openLogin(page);

  // 必须是真链接，不是一段普通文字
  expect(await page.getByTestId('agreement-service').evaluate((n) => n.tagName.toLowerCase()))
    .toBe('a');

  const checkedBefore = await page
    .locator('.login-agreement .el-checkbox__input')
    .evaluate((n) => n.classList.contains('is-checked'));

  await page.getByTestId('agreement-service').click();

  const dialog = page.getByTestId('agreement-dialog');
  await expect(dialog, '点协议应弹出阅读窗').toBeVisible({ timeout: 10_000 });
  await expect(dialog).toContainText('森特商城用户服务协议');
  await expect(dialog).toContainText('协议的范围');
  await expect(dialog).toContainText('账号注册与使用');
  await expect(dialog).toContainText('退换货与售后');
  await expect(dialog).toContainText('协议变更与争议解决');

  // 正文足够长：必须能滚动，否则「阅读全文」无从谈起
  const body = page.getByTestId('agreement-body');
  const { scrollHeight, clientHeight } = await body.evaluate((n) => ({
    scrollHeight: n.scrollHeight,
    clientHeight: n.clientHeight,
  }));
  console.log('协议正文高度:', scrollHeight, '可视高度:', clientHeight);
  expect(scrollHeight, '协议正文应超出一屏（可滚动阅读）').toBeGreaterThan(clientHeight);

  // 打开协议不得改动勾选状态，也不得跳出登录页
  const checkedAfter = await page
    .locator('.login-agreement .el-checkbox__input')
    .evaluate((n) => n.classList.contains('is-checked'));
  expect(checkedAfter, '点协议不能顺手把勾选框点掉').toBe(checkedBefore);
  expect(page.url(), '点协议不应离开登录页').toContain('/login');

  await page.getByTestId('agreement-close').click();
  await expect(dialog).toBeHidden({ timeout: 10_000 });

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

test('登录页《隐私权政策》可点击并阅读全文', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await openLogin(page);
  await page.getByTestId('agreement-privacy').click();

  const dialog = page.getByTestId('agreement-dialog');
  await expect(dialog, '点隐私政策应弹出阅读窗').toBeVisible({ timeout: 10_000 });
  await expect(dialog).toContainText('隐私权政策');
  await expect(dialog).toContainText('我们收集哪些信息');
  await expect(dialog).toContainText('我们如何使用这些信息');
  await expect(dialog).toContainText('未成年人保护');
  // 两份文档不能串台：服务协议特有的章节不该出现在隐私政策里
  await expect(dialog).not.toContainText('协议的范围');

  await page.getByTestId('agreement-close').click();
  await expect(dialog).toBeHidden({ timeout: 10_000 });

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});
