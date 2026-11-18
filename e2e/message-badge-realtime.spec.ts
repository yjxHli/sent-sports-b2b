/**
 * 站内信红点「实时」跟随已读（2026-11-16，TDD 红→绿）。
 *
 * 用户诉求（第 4 轮第 5 点）：站内信的红点要随着「全部已读 / 部分已读」实时变动。
 *
 * 原实现：顶栏红点与消息页各存一份 `unreadCount`，消息页标记已读只改自己那份，
 * 顶栏要等路由切走（或 60s 轮询）才更新——用户看到的就是「已经读了，红点还在」。
 * 现在未读数收口到 `useMessageStore`，任何读动作都刷新同一份状态。
 *
 * 断言口径（全程停在同一页，不刷新、不切路由）：
 *   1) 进消息页时顶栏红点 = 3（种子 3 条未读）；
 *   2) 点一条未读 → 顶栏红点变 2、页头「未读 2 条」；
 *   3) 点「全部已读」→ 顶栏红点消失、页头「未读 0 条」；
 *   4) 收尾把演示账号的收件箱还原（不重灌未读，否则用户会看到「重启又变未读」）；
 *   5) 无 console error。
 */
import { test, expect, type Page } from '@playwright/test';
import { restoreInbox, snapshotInbox } from './fixtures/inbox';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

/**
 * 9002 是商城演示账号本身：直接 DELETE + 重灌会让用户看到
 * 「站内信每次重启又变未读」。统一走快照 → 播种 → 还原（见 fixtures/inbox.ts）。
 */
const CUSTOMER_ID = 9002;

/** 固定 3 条未读：红点数字才有确定基准（演示数据被读过一次就再也回不到 3） */
function resetMessages(): void {
  snapshotInbox(CUSTOMER_ID, `DELETE FROM user_message WHERE customer_id = ${CUSTOMER_ID};
INSERT INTO user_message (customer_id, msg_type, title, content, biz_type, biz_no, read_flag) VALUES
(${CUSTOMER_ID}, 'ORDER', '订单已发货', '您的订单已由顺丰发出，请注意查收', 'ORDER', NULL, 0),
(${CUSTOMER_ID}, 'PROMO', '会员日专享优惠', '本月会员日满 300 减 30', 'COUPON', NULL, 0),
(${CUSTOMER_ID}, 'SYSTEM', '账号安全提醒', '检测到您在新设备登录', NULL, NULL, 0);`);
}

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

test('顶栏红点跟随「部分已读 / 全部已读」实时变化', async ({ page }) => {
  test.setTimeout(120_000);
  const consoleErrors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) consoleErrors.push(m.text());
  });

  resetMessages();
  await loginUser(page);

  await page.goto(`${USER_URL.replace(/\/+$/, '')}/messages`);
  const badge = page.locator('.topbar__msg .msg-badge');
  const headUnread = page.locator('.msg-head__unread');

  // 1) 初始：3 条未读
  await expect(badge, '顶栏红点应显示 3 条未读').toHaveText('3', { timeout: 20_000 });
  await expect(headUnread, '页头未读数应与红点一致').toContainText('未读 3 条');

  // 2) 部分已读：点第一条未读，红点实时 3 → 2（不切路由、不刷新）
  await page.locator('.msg-item--unread').first().click();
  await expect(badge, '标记一条已读后红点应立刻变成 2').toHaveText('2', { timeout: 20_000 });
  await expect(headUnread, '页头未读数应同步变成 2').toContainText('未读 2 条');

  // 3) 全部已读：红点消失，页头归零
  await page.getByRole('button', { name: '全部已读' }).click();
  await expect(badge, '全部已读后顶栏红点应消失').toHaveCount(0, { timeout: 20_000 });
  await expect(headUnread, '页头未读数应归零').toContainText('未读 0 条');
  await expect(page.locator('.msg-item--unread'), '不应再有未读样式').toHaveCount(0);

  expect(consoleErrors, `console errors: ${consoleErrors.join(' | ')}`).toHaveLength(0);

  // 4) 收尾必须还原用例开始前的收件箱：这是演示账号的真实收件箱，
  //    以前「跑完重新灌 3 条未读」正是用户看到「重启又变未读」的根因
  restoreInbox(CUSTOMER_ID);
});
