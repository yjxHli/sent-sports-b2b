/**
 * 站内信「消息」中心验收（TDD 红→绿）。
 *
 * 用户诉求：消息和在线客服不是一个东西 —— 消息是站内信（订单动态/优惠/系统公告），
 * 客服是实时会话，两个入口必须分开。
 *
 * 断言口径：
 *   1) 顶栏「消息」指向 /messages，顶栏「在线客服」指向 /chat，两者不同；
 *   2) 消息页有列表、类型筛选（全部/订单/优惠/系统）；
 *   3) 点一条未读 → 该条变已读（列表样式与未读计数同步）；
 *   4) 「全部已读」后未读数归零；
 *   5) 无 console error。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';

const USER_URL = process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173';
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';

const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

/**
 * 演示站内信（V25 灌的 6 条）只会被「已读」消耗掉：第一次跑完再看全是已读，
 * 「全部已读」按钮就处于禁用态（这是产品正确行为），用例会假失败。
 * 因此在开跑前把 9002 的消息重置成固定的 3 条未读，保证用例可重复执行。
 * 可用 E2E_MYSQL_BIN / E2E_DB_PASSWORD 覆盖默认值。
 */
const CUSTOMER_ID = 9002;
/** 本机 MySQL 客户端不在 PATH 上时的默认位置（与 README 的环境一致） */
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

/** 三条固定演示消息（订单/优惠/系统各一条，全未读）：删除与清空用例都要有确定的初始状态 */
const SEED_MESSAGES = `DELETE FROM user_message WHERE customer_id = ${CUSTOMER_ID};
INSERT INTO user_message (customer_id, msg_type, title, content, biz_type, biz_no, read_flag) VALUES
(${CUSTOMER_ID}, 'ORDER', '订单已发货', '您的订单已由顺丰发出，请注意查收', 'ORDER', 'SO20261115904218', 0),
(${CUSTOMER_ID}, 'PROMO', '会员日专享优惠', '本月会员日满 300 减 30，活动截止 11 月 30 日', 'COUPON', NULL, 0),
(${CUSTOMER_ID}, 'SYSTEM', '账号安全提醒', '检测到您在新设备登录，如非本人操作请及时修改密码', NULL, NULL, 0);`;

function resetMessages(): void {
  runSql(SEED_MESSAGES);
}

test.beforeAll(() => {
  resetMessages();
});

/** 清空用例会真的把消息删干净：跑完重新灌回固定 3 条，下一次运行仍有数据可验 */
test.afterAll(() => {
  resetMessages();
});

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

test('消息中心是站内信，与在线客服入口分离', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);

  // 1) 两个入口必须分开
  const msgLink = page.locator('.topbar__link', { hasText: '消息' }).first();
  await expect(msgLink, '顶栏应有「消息」入口').toBeVisible();
  await expect(msgLink, '「消息」应指向站内信 /messages').toHaveAttribute('href', /\/messages/);
  const chatLink = page.locator('.topbar__link', { hasText: '在线客服' }).first();
  await expect(chatLink, '「在线客服」应指向 /chat').toHaveAttribute('href', /\/chat/);

  // 2) 进入消息中心
  await msgLink.click();
  await expect(page).toHaveURL(/\/messages/, { timeout: 15_000 });

  const tabs = page.locator('.msg-tab');
  await expect(tabs, '消息类型筛选应有 4 个').toHaveCount(4);
  for (const name of ['全部', '订单', '优惠', '系统']) {
    await expect(tabs.filter({ hasText: name }).first()).toBeVisible();
  }

  const items = page.locator('.msg-item');
  await expect(items.first(), '消息列表应有数据').toBeVisible({ timeout: 20_000 });

  // 3) 点一条未读 → 该条变已读
  const unread = page.locator('.msg-item--unread');
  const unreadBefore = await unread.count();
  if (unreadBefore > 0) {
    const target = unread.first();
    const title = (await target.locator('.msg-item__title').innerText()).trim();
    await target.click();
    // 该条的未读样式应消失（用标题定位，避免序号漂移）
    await expect(
      page.locator('.msg-item', { hasText: title }).first(),
      '点过之后该条应变为已读',
    ).not.toHaveClass(/msg-item--unread/, { timeout: 15_000 });
    await expect(unread, '未读数应减少 1').toHaveCount(unreadBefore - 1, { timeout: 15_000 });
  }

  // 4) 全部已读 → 未读归零
  const readAll = page.getByRole('button', { name: /全部已读/ }).first();
  await expect(readAll).toBeVisible();
  await readAll.click();
  await expect(page.locator('.msg-item--unread'), '全部已读后不应再有未读').toHaveCount(0, {
    timeout: 15_000,
  });

  // 5) 类型筛选：只看系统公告
  const systemTab = tabs.filter({ hasText: '系统' }).first();
  const systemResp = page.waitForResponse(
    (r) => r.url().includes('/api/messages') && r.url().includes('type=SYSTEM'),
    { timeout: 20_000 },
  );
  await systemTab.click();
  await systemResp;
  const types = await page.locator('.msg-item__type').allInnerTexts();
  if (types.length > 0) {
    expect(types.every((t) => t.includes('系统')), `筛选后应只有系统消息，实际：${types}`).toBe(true);
  }

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});

/**
 * 删除单条 + 一键清空（2026-11-15 用户诉求：「添加一个可以一键清空所有站内信的功能，
 * 也可以选择删除指定的」）。
 *
 * 口径：
 *   1) 每条有「删除」，二次确认后该条消失，未读总数同步减少；
 *   2) 顶部「清空」二次确认后全部消息消失，列表进入空态，按钮回到禁用；
 *   3) 清空后未读数归零（顶栏红点不该还挂着）。
 */
test('消息中心支持删除指定消息与一键清空', async ({ page }) => {
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  await loginUser(page);
  await page.goto(`${USER_URL.replace(/\/+$/, '')}/messages`);

  const items = page.locator('.msg-item');
  await expect(items.first(), '消息列表应有数据').toBeVisible({ timeout: 20_000 });
  const before = await items.count();
  expect(before, 'beforeAll 已重置为 3 条').toBeGreaterThan(0);

  // 1) 删除指定一条
  const firstTitle = (await items.first().locator('.msg-item__title').innerText()).trim();
  await items.first().locator('.msg-item__del').click();
  const confirmDel = page.locator('.el-message-box');
  await expect(confirmDel, '删除应有二次确认').toBeVisible({ timeout: 10_000 });
  const delResp = page.waitForResponse(
    (r) => /\/api\/messages\/\d+$/.test(r.url()) && r.request().method() === 'DELETE',
    { timeout: 20_000 },
  );
  await confirmDel.getByRole('button', { name: '删除' }).click();
  const delBody: any = await (await delResp).json().catch(() => null);
  expect(delBody?.code, `删除失败：${JSON.stringify(delBody)}`).toBe(0);

  await expect(items, '删除后应少一条').toHaveCount(before - 1, { timeout: 15_000 });
  await expect(
    page.locator('.msg-item', { hasText: firstTitle }),
    '被删除的那条不应再出现',
  ).toHaveCount(0);

  // 2) 一键清空
  const clearBtn = page.getByRole('button', { name: '清空' }).first();
  await expect(clearBtn, '顶部应有「清空」入口').toBeEnabled();
  await clearBtn.click();
  const confirmClear = page.locator('.el-message-box');
  await expect(confirmClear, '清空应有二次确认').toBeVisible({ timeout: 10_000 });
  const clearResp = page.waitForResponse(
    (r) => /\/api\/messages$/.test(r.url()) && r.request().method() === 'DELETE',
    { timeout: 20_000 },
  );
  await confirmClear.getByRole('button', { name: '全部清空' }).click();
  const clearBody: any = await (await clearResp).json().catch(() => null);
  expect(clearBody?.code, `清空失败：${JSON.stringify(clearBody)}`).toBe(0);
  expect(Number(clearBody?.data ?? 0), '清空应返回删除条数').toBe(before - 1);

  await expect(items, '清空后列表应为空').toHaveCount(0, { timeout: 15_000 });
  await expect(page.getByText('暂无消息'), '清空后应显示空态').toBeVisible({ timeout: 15_000 });
  await expect(clearBtn, '没有消息时「清空」应禁用').toBeDisabled();
  await expect(page.locator('.msg-head__unread'), '清空后未读数应归零').toContainText('0');

  expect(errors, `console errors: ${errors.join(' | ')}`).toHaveLength(0);
});
