/**
 * 管理端侧栏「取消手风琴」（2026-11-26 用户诉求，TDD 先红后绿）。
 *
 * 用户口径：
 *   1) 打开「订单」组再打开「客户与会员」组，订单组绝不能自动收起；
 *   2) 在多组之间来回切页面，之前手动展开的组也要保留（提升多模块切换效率）；
 *   3) 刷新/外部跳转到某个页面，包含当前路由的组自动展开；
 *   4) 展开太多组时侧栏内部滚动，而不是整个页面滚动。
 *
 * 红线：只断言菜单配置与外层滚动，不碰 DOM 结构和 data-testid。
 */
import { test, expect, type Page, type Locator } from '@playwright/test';

const ADMIN_URL = (process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/').replace(/\/+$/, '');

async function login(page: Page): Promise<void> {
  await page.goto(`${ADMIN_URL}/login`);
  await page.waitForTimeout(600);
  if (page.url().includes('login')) {
    await page.getByPlaceholder('用户名').fill('admin');
    await page.getByPlaceholder('密码').fill('123456');
    await page.getByRole('button', { name: /登\s*录/ }).click();
  }
  await page.waitForSelector('.sent-menu .el-menu-item', { timeout: 30_000 });
}

function group(page: Page, title: string): Locator {
  return page.locator('.sent-menu > .el-sub-menu').filter({
    has: page.locator('.el-sub-menu__title', { hasText: new RegExp(`^${title}$`) }),
  });
}

async function isOpen(el: Locator): Promise<boolean> {
  // aria-expanded 落在 .el-sub-menu 根节点上（不是内部 .el-sub-menu__title）
  return (await el.getAttribute('aria-expanded')) === 'true';
}

test('侧栏：多个分组可同时展开，跨模块来回切换也不自动收起', async ({ page }) => {
  await login(page);
  await page.goto(`${ADMIN_URL}/dashboard`);
  await page.waitForSelector('.sent-menu .el-menu-item');

  const order = group(page, '订单');
  const customer = group(page, '客户与会员');
  const stock = group(page, '库存');

  // 1) 先点订单、再点客户与会员：订单仍展开
  await order.locator('.el-sub-menu__title').click();
  expect(await isOpen(order), '订单组应展开').toBe(true);
  await customer.locator('.el-sub-menu__title').click();
  expect(await isOpen(customer), '客户与会员组应展开').toBe(true);
  expect(await isOpen(order), '打开新组时订单组不得自动收起').toBe(true);

  // 2) 再打开第三组，前三组并存
  await stock.locator('.el-sub-menu__title').click();
  expect(await isOpen(stock), '库存组应展开').toBe(true);
  expect(await isOpen(order)).toBe(true);
  expect(await isOpen(customer)).toBe(true);

  // 3) 跨组点菜单项：URL 正确，且已展开的其它组不收起
  await order.locator('.el-menu-item', { hasText: '订单列表' }).click();
  await expect(page).toHaveURL(/\/order\/list/, { timeout: 20_000 });
  await expect(page.getByRole('heading', { name: '订单列表' })).toBeVisible();
  expect(await isOpen(customer), '跳到订单页后客户与会员组仍应展开').toBe(true);
  expect(await isOpen(stock)).toBe(true);

  await customer.locator('.el-menu-item', { hasText: '客户列表' }).click();
  await expect(page).toHaveURL(/\/customer\/list/, { timeout: 20_000 });
  expect(await isOpen(order), '跳到客户页后订单组仍应展开').toBe(true);

  // 4) 再点回订单，导航依旧正确
  await order.locator('.el-menu-item', { hasText: '订单列表' }).click();
  await expect(page).toHaveURL(/\/order\/list/, { timeout: 20_000 });
  expect(await isOpen(customer)).toBe(true);
});

test('侧栏：外部跳转自动展开当前路由组，组太多时侧栏内部滚动', async ({ page }) => {
  await login(page);

  // 1) 外部直达：财务组必须自动展开
  await page.goto(`${ADMIN_URL}/finance/invoice`);
  await expect(page.getByRole('heading', { name: '发票管理' })).toBeVisible({ timeout: 20_000 });
  await expect.poll(async () => isOpen(group(page, '财务'))).toBe(true);

  // 2) 打开全部组：侧栏内部滚动，body 不滚动
  await page.goto(`${ADMIN_URL}/dashboard`);
  await page.waitForSelector('.sent-menu .el-menu-item');
  const groups = page.locator('.sent-menu > .el-sub-menu');
  const count = await groups.count();
  expect(count, '演示账号应看到多个分组').toBeGreaterThan(5);
  for (let i = 0; i < count; i += 1) {
    const item = groups.nth(i);
    if ((await item.getAttribute('aria-expanded')) !== 'true') {
      await item.locator('.el-sub-menu__title').click();
    }
  }

  const metrics = await page.evaluate(() => {
    const aside = document.querySelector('.sent-aside') as HTMLElement;
    return {
      bodyOverflow: document.documentElement.scrollHeight - window.innerHeight,
      asideOverflowY: getComputedStyle(aside).overflowY,
      asideScrollable: aside.scrollHeight > aside.clientHeight,
    };
  });
  expect(metrics.asideOverflowY, '侧栏自身要有滚动容器').toBe('auto');
  expect(metrics.asideScrollable, '展开全部组后侧栏应可滚动').toBe(true);
  expect(metrics.bodyOverflow, '侧栏变长不应把整页撑出滚动条').toBeLessThanOrEqual(1);
});
