/**
 * 管理端侧边栏排版统一（2026-11-25 用户诉求，TDD 红→绿）。
 *
 * 要求原话逐条落进断言：
 *   - 保留内容、配色与交互：菜单文字/路径不变，点分组仍展开、点条目仍跳转；
 *   - 沿用现有样式，统一字号、圆角和间距；
 *   - 同级面板圆角一致，侧栏两侧留白一致；
 *   - 图标略小于文字，图文间距保持一致；
 *   - 每行放得下文字和上下留白，点击区域足够大；
 *   - 组间比组内更松，间距遵循统一规则。
 *
 * 用 DOM 实测而不是截图比对：行高/圆角/左右留白/图文间距/纵向间距都可量化。
 */
import { test, expect, type Page } from '@playwright/test';

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

interface Row {
  label: string;
  kind: 'root' | 'group' | 'child';
  x: number;
  w: number;
  h: number;
  y: number;
  radius: string;
  fontSize: number;
  iconW: number | null;
  iconGap: number | null;
  overflow: boolean;
}

async function rows(page: Page): Promise<Row[]> {
  return page.evaluate(() => {
    const typeOf = (el: HTMLElement): Row['kind'] => {
      if (el.classList.contains('el-sub-menu__title')) return 'group';
      if (el.closest('.el-sub-menu')) return 'child';
      return 'root';
    };
    const nodes = [
      ...document.querySelectorAll<HTMLElement>('.sent-menu > .el-menu-item'),
      ...document.querySelectorAll<HTMLElement>('.sent-menu > .el-sub-menu > .el-sub-menu__title'),
      ...document.querySelectorAll<HTMLElement>('.sent-menu .el-menu-item[style], .sent-menu .el-sub-menu .el-menu-item'),
    ];
    const seen = new Set<HTMLElement>();
    return nodes.filter((el) => {
      const b = el.getBoundingClientRect();
      if (!b.height || seen.has(el)) return false;
      seen.add(el);
      return true;
    }).map((el) => {
      const b = el.getBoundingClientRect();
      const cs = getComputedStyle(el);
      const icon = el.querySelector<HTMLElement>('.nav-icon svg');
      const label = el.querySelector<HTMLElement>('.nav-text');
      let iconW: number | null = null;
      let iconGap: number | null = null;
      if (icon && label) {
        iconW = Math.round(icon.getBoundingClientRect().width);
        const range = document.createRange();
        range.selectNodeContents(label);
        const textBox = range.getBoundingClientRect();
        iconGap = Math.round(textBox.left - icon.getBoundingClientRect().right);
      }
      return {
        label: (label?.textContent ?? el.textContent ?? '').trim(),
        kind: typeOf(el),
        x: Math.round(b.x), w: Math.round(b.width), h: Math.round(b.height), y: Math.round(b.y),
        radius: cs.borderRadius, fontSize: Number.parseFloat(cs.fontSize),
        iconW, iconGap, overflow: el.scrollWidth > el.clientWidth + 1,
      };
    });
  });
}

test('侧栏：字号圆角间距统一、图标小于文字、组间松于组内、点击与跳转保留', async ({ page }) => {
  await login(page);
  await page.goto(`${ADMIN_URL}/dashboard`);
  await page.waitForSelector('.sent-menu .el-menu-item');

  // 展开「商品」分组，才能量到组内条目；默认展开策略（当前路由所属组）保持不变
  const goodsGroup = page.locator('.sent-menu > .el-sub-menu').filter({
    has: page.locator('.el-sub-menu__title', { hasText: '商品' }),
  });
  await goodsGroup.locator('.el-sub-menu__title').click();
  await expect(goodsGroup.locator('.el-menu-item').first()).toBeVisible();

  const list = await rows(page);
  expect(list.filter((r) => r.kind === 'root').map((r) => r.label)).toEqual(['工作台', '业务报表']);
  expect(list.some((r) => r.label === '商品列表')).toBeTruthy();

  const asideW = await page.locator('.sent-aside').evaluate((el: HTMLElement) => Math.round(el.getBoundingClientRect().width));
  for (const r of list) {
    // 侧栏两侧留白一致：所有行同宽、同一个 10px 外边距
    expect(r.x, `${r.label} 左边距应与其它行一致`).toBe(10);
    expect(r.w, `${r.label} 宽度应留同样的右侧留白`).toBe(asideW - 20);
    // 同级面板圆角一致 + 字号统一
    expect(r.radius, `${r.label} 圆角`).toBe('8px');
    expect(r.fontSize, `${r.label} 字号`).toBe(14);
    // 每行放得下文字：不横向溢出；行高 ≥40 且统一（文字 + 上下留白 + 足够点击区域）
    expect(r.overflow, `${r.label} 文字溢出行宽`).toBeFalsy();
    expect(r.h, `${r.label} 行高`).toBe(40);
    // 图标：每行都有，且略小于 14px 文字；图标与文字的间距统一
    expect(r.iconW, `${r.label} 缺图标`).not.toBeNull();
    expect(r.iconW!, `${r.label} 图标比文字还大`).toBeGreaterThan(0);
    expect(r.iconW!).toBeLessThan(14);
    expect(r.iconGap, `${r.label} 图文间距`).toBeGreaterThanOrEqual(6);
    expect(r.iconGap!).toBeLessThanOrEqual(10);
  }

  // 组间比组内松：同一分组内相邻条目留 2px，相邻分组标题留 14px 以上
  const children = list.filter((r) => r.kind === 'child').sort((a, b) => a.y - b.y);
  const groups = list.filter((r) => r.kind === 'group').sort((a, b) => a.y - b.y);
  const innerGaps: number[] = [];
  for (let i = 1; i < children.length; i += 1) {
    innerGaps.push(children[i].y - (children[i - 1].y + children[i - 1].h));
  }
  expect(innerGaps.length).toBeGreaterThan(1);
  for (const gap of innerGaps) {
    expect(gap, `组内间距 ${gap}px，应是固定的小间距`).toBe(2);
  }
  const outerGaps: number[] = [];
  for (let i = 1; i < groups.length; i += 1) {
    outerGaps.push(groups[i].y - (groups[i - 1].y + groups[i - 1].h));
  }
  expect(outerGaps.length).toBeGreaterThan(1);
  for (const gap of outerGaps) {
    expect(gap, `组间间距 ${gap}px，应明显大于组内`).toBeGreaterThanOrEqual(14);
  }

  // 交互保留：点分组展开/收起，点条目仍跳原路径
  await goodsGroup.locator('.el-sub-menu__title').click();
  await expect(goodsGroup.locator('.el-menu-item').first()).toBeHidden();
  await goodsGroup.locator('.el-sub-menu__title').click();
  await goodsGroup.locator('.el-menu-item', { hasText: '商品列表' }).click();
  await expect(page).toHaveURL(/\/product\/list/, { timeout: 20_000 });
});
