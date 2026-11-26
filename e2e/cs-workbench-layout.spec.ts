/**
 * 客服工作台三栏布局（2026-11-25 第三轮用户诉求，TDD 红→绿）。
 *
 * 诉求：
 *   1) 左侧坐席/会话栏收窄，省出的宽度给中间聊天区；
 *   2) 中间聊天区和右侧服务记录之间也要像左侧那样留出间隔（三块各自是卡片）；
 *   3) 顶栏（森特客服工作台所在容器）底色太深，要浅色。
 *
 * 断言全部走 DOM 实测：不看类名写法，只看用户看到的宽窄与颜色。
 */
import { test, expect } from '@playwright/test';
import {
  ADMIN_NAME, ADMIN_PASSWORD, CS_URL, apiCall, clearOpenSessions, join,
  loginApi, loginCs, setStaffPresence, startConsultation,
} from './chat-helpers';

const STAFF_ID = 9001;
const CUSTOMER_ID = 9002;

test('工作台布局：左栏收窄、三块等距留白、顶栏浅色', async ({ browser, request }) => {
  test.setTimeout(120_000);
  const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin', `e2e-layout-a-${Date.now()}`);
  const userToken = await loginApi(request, '13800138000', '123456', 'user', `e2e-layout-u-${Date.now()}`);

  await setStaffPresence(request, adminToken, [STAFF_ID], 'OFFLINE');
  await clearOpenSessions(request, adminToken);
  await setStaffPresence(request, adminToken, [STAFF_ID], 'ONLINE');

  const created = await apiCall(request, 'POST', '/chat/sessions', { customerId: CUSTOMER_ID, vip: false }, userToken);
  const sessionId = Number(created?.data?.sessionId);
  expect(sessionId).toBeGreaterThan(0);
  await startConsultation(request, sessionId, userToken);

  const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 } });
  const page = await ctx.newPage();
  try {
    await loginCs(page, ADMIN_NAME, ADMIN_PASSWORD);
    await page.goto(join(CS_URL, '/workbench'));
    await expect(page.locator(`.session-item[data-session-id="${sessionId}"]`))
      .toBeVisible({ timeout: 20_000 });
    await page.locator(`.session-item[data-session-id="${sessionId}"]`).click();
    await expect(page.locator('.chat-panel')).toBeVisible({ timeout: 20_000 });

    const box = async (sel: string) => page.locator(sel).first().evaluate((el) => {
      const r = el.getBoundingClientRect();
      return { left: r.left, right: r.right, width: r.width };
    });

    // 1) 左栏收窄：1600 视口下不超过 290px（改前 320）
    const side = await box('.wb__side');
    expect(side.width, `左侧栏宽度 ${side.width}`).toBeLessThanOrEqual(290);
    expect(side.width, `左侧栏宽度 ${side.width}`).toBeGreaterThanOrEqual(230);

    // 2) 左↔中、中↔右 两处间隔相等且确实存在（各约 12px，允许 ±4）
    const chat = await box('.chat-panel');
    const record = await box('.cust-orders');
    const gapLeftMid = chat.left - side.right;
    const gapMidRight = record.left - chat.right;
    expect(gapLeftMid, `左↔中间隔 ${gapLeftMid}`).toBeGreaterThanOrEqual(6);
    expect(gapMidRight, `中↔右间隔 ${gapMidRight}`).toBeGreaterThanOrEqual(6);
    expect(Math.abs(gapLeftMid - gapMidRight), `两侧间隔应一致：${gapLeftMid} vs ${gapMidRight}`)
      .toBeLessThanOrEqual(4);

    // 3) 右侧服务记录自己是卡片（有圆角），不再是贴边的内嵌列
    const radius = await page.locator('.cust-orders').first()
      .evaluate((el) => parseFloat(getComputedStyle(el).borderTopLeftRadius));
    expect(radius, `右侧服务记录圆角 ${radius}`).toBeGreaterThan(6);

    // 4) 顶栏不再是深色底：渐变里的每个色都要够浅
    const bg = await page.locator('.cs-topbar').evaluate((el) => getComputedStyle(el).backgroundImage);
    const tones = [...bg.matchAll(/rgba?\((\d+),\s*(\d+),\s*(\d+)/g)]
      .map((m) => (Number(m[1]) + Number(m[2]) + Number(m[3])) / 3);
    expect(tones.length, `顶栏背景应可解析：${bg}`).toBeGreaterThan(0);
    expect(Math.min(...tones), `顶栏最暗色阶 ${Math.min(...tones)}，背景：${bg}`).toBeGreaterThan(190);
  } finally {
    await clearOpenSessions(request, adminToken).catch(() => undefined);
    await setStaffPresence(request, adminToken, [STAFF_ID], 'OFFLINE').catch(() => undefined);
    await ctx.close();
  }
});
