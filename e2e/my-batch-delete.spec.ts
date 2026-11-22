/**
 * 我的订单 / 我的定制「多选删除」（2026-11-21 用户需求）。
 *
 * 用户原话：「在我的订单的商品订单和定制订单这里增加一个可以删除订单的功能（可以选择删除哪些）」。
 *
 * 断言口径（两端同构，故放在一个 spec 里跑）：
 *   0) 页面默认没有勾选框，点「批量管理」才出现复选框 + 批量操作条（不打扰日常浏览）；
 *   1) 只有终态能勾：商品订单 = 已完成/已取消/已关闭，定制单 = 已发货/已取消；
 *      在途订单、进行中的定制单复选框是 disabled，且「全选本页」也不会把它们塞进选择集；
 *   2) 删除必须二次确认，确认后真正打 batch-delete 接口，卡片从列表消失；
 *   3) 服务端才是最终防线：即使绕过 UI 直接调接口，进行中的定制单也删不掉，只回一条「进行中」原因；
 *   4) 软删不动业务数据：库里只多一个 customer_deleted_at，status 不变（财务/客服读面照旧）。
 *
 * 数据策略：商品订单取列表里现成的已完成单（跑完把 customer_deleted_at 复位，可重复执行）；
 * 定制单库里没有终态演示数据，用 SQL 造两条临时单（一条已发货、一条生产中），跑完删掉。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';

const USER_URL = (process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');
const CUSTOMER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const CUSTOMER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';
/** 演示库里的客户主键（13800138000），与 OrderApiTest 同口径 */
const CUSTOMER_ID = Number(process.env.E2E_CUSTOMER_ID ?? 9002);

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

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL}/login`);
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

/** 从卡片头「订单号：SO2026...」里抠出单号 */
function orderNoOf(text: string): string {
  return (text.match(/SO[0-9A-Za-z_-]+/) ?? [''])[0];
}

test('商品订单：批量管理可勾选终态单并删除，在途单不可勾选也不能被接口删掉', async ({ page, request }) => {
  await loginUser(page);

  // ---------- 1) 已完成 Tab：能勾、能删 ----------
  await page.goto(`${USER_URL}/orders?status=COMPLETED`);
  await expect(page.locator('.order-card').first()).toBeVisible({ timeout: 20_000 });

  // 进页面前没有勾选框：批量是「按需进入」的模式，不打扰日常浏览
  await expect(page.locator('[data-testid="order-select"]')).toHaveCount(0);

  const firstCard = page.locator('.order-card').first();
  const orderNo = orderNoOf(await firstCard.locator('.order-card__no').innerText());
  expect(orderNo, '第一张卡片必须有订单号').not.toBe('');
  const statusBefore = sql(`SELECT status FROM orders WHERE order_no='${orderNo}'`);

  try {
    await page.getByTestId('order-batch-toggle').click();
    await expect(page.getByTestId('order-batch-bar')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId('order-batch-bar')).toContainText('仅「已完成 / 已取消 / 已关闭」可删除');

    // Element Plus 的禁用态落在 label 的 is-disabled 类上，勾选框本体是隐藏 input，用类断言最稳
    const firstCheckbox = firstCard.locator('[data-testid="order-select"]');
    await expect(firstCheckbox, '已完成订单的复选框必须可用').not.toHaveClass(/is-disabled/);
    await firstCheckbox.click();
    await expect(page.getByTestId('order-batch-bar').locator('.batch-bar__hint')).toContainText('已选 1');

    await page.getByTestId('order-batch-delete').click();
    const box = page.locator('.el-message-box');
    await expect(box, '删除订单必须二次确认').toBeVisible({ timeout: 15_000 });
    await expect(box).toContainText('确定删除选中的 1 笔订单');

    const posted = page.waitForResponse(
      (r) => r.url().includes('/api/orders/batch-delete') && r.request().method() === 'POST',
      { timeout: 20_000 },
    );
    await box.getByRole('button', { name: '删除', exact: true }).click();
    const body: any = await (await posted).json().catch(() => null);
    expect(body?.code, `批量删除接口异常：${JSON.stringify(body)}`).toBe(0);
    expect(body?.data?.deleted, '选中的终态单应当被删掉').toBe(1);

    await expect(page.locator('.el-message').filter({ hasText: '已删除 1 笔订单' })).toBeVisible({
      timeout: 15_000,
    });
    // 卡片从「我的订单」消失（软删后列表过滤）
    await expect(
      page.locator('.order-card').filter({ hasText: orderNo }),
      '删除后该订单不应再出现在我的订单列表',
    ).toHaveCount(0, { timeout: 20_000 });

    // 软删只打标记：业务状态不变，财务/客服读面仍可查
    expect(sql(`SELECT customer_deleted_at IS NOT NULL FROM orders WHERE order_no='${orderNo}'`)).toBe('1');
    expect(sql(`SELECT status FROM orders WHERE order_no='${orderNo}'`), '删除不能改业务状态').toBe(statusBefore);
    expect(sql(`SELECT admin_deleted_at IS NULL FROM orders WHERE order_no='${orderNo}'`)).toBe('1');
  } finally {
    // 复位：让用例可重复执行，也不影响其它用例读「已完成」列表
    sql(`UPDATE orders SET customer_deleted_at=NULL WHERE order_no='${orderNo}'`);
  }

  // ---------- 2) 待发货 Tab：在途单勾不动，接口也删不掉 ----------
  await page.goto(`${USER_URL}/orders?status=WAIT_SHIP`);
  await expect(page.locator('.order-card').first()).toBeVisible({ timeout: 20_000 });
  const inFlightNo = orderNoOf(await page.locator('.order-card').first().locator('.order-card__no').innerText());

  await page.getByTestId('order-batch-toggle').click();
  await expect(
    page.locator('[data-testid="order-select"]').first(),
    '待发货订单不能勾选（删了客服就找不到单）',
  ).toHaveClass(/is-disabled/);
  await expect(
    page.getByTestId('order-batch-bar').locator('[data-testid="order-batch-all"]'),
    '在途 Tab 里全选也必须不可用（避免「全选后删除」把在途单卷进来）',
  ).toHaveClass(/is-disabled/);
  await expect(page.getByTestId('order-batch-delete')).toBeDisabled();

  // 绕过 UI 直接打接口：服务端只回「在途」原因，不改库
  const token = await page.evaluate(() => window.localStorage.getItem('sent_user_access_token') ?? '');
  expect(token, '登录后应拿到用户端令牌').not.toBe('');
  const guard = await request.post(`${API_URL}/orders/batch-delete`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { ids: [inFlightNo] },
    failOnStatusCode: false,
  });
  const guardBody: any = await guard.json().catch(() => null);
  expect(guardBody?.code).toBe(0);
  expect(guardBody?.data?.deleted, '在途订单不能删').toBe(0);
  expect(String(guardBody?.data?.skipped?.[0]?.reason ?? '')).toContain('在途');
  expect(sql(`SELECT customer_deleted_at IS NULL FROM orders WHERE order_no='${inFlightNo}'`)).toBe('1');
});

test('我的定制：只有已发货/已取消可勾选，删除后从列表消失，进行中的单服务端兜住', async ({ page, request }) => {
  const stamp = Date.now();
  const shippedNo = `CUE2EBDS${stamp}`;
  const producingNo = `CUE2EBDP${stamp}`;
  sql('INSERT INTO custom_order (custom_no, order_item_id, customer_id, service_type, print_side, qty, fee_cent, status)'
    + ` VALUES ('${shippedNo}', NULL, ${CUSTOMER_ID}, 'ATTACHED', 'SINGLE', 2, 2000, 'SHIPPED'),`
    + ` ('${producingNo}', NULL, ${CUSTOMER_ID}, 'ATTACHED', 'SINGLE', 2, 2000, 'IN_PRODUCTION')`);

  try {
    await loginUser(page);
    await page.goto(`${USER_URL}/custom`);
    const shippedRow = page.locator('.custom-row').filter({ hasText: shippedNo });
    const producingRow = page.locator('.custom-row').filter({ hasText: producingNo });
    await expect(shippedRow, '新造的定制单应排在「我的定制」第一页（列表按创建时间倒序）').toBeVisible({
      timeout: 20_000,
    });
    await expect(producingRow).toBeVisible();

    await expect(page.locator('[data-testid="custom-select"]')).toHaveCount(0);
    await page.getByTestId('custom-batch-toggle').click();
    await expect(page.getByTestId('custom-batch-bar')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId('custom-batch-bar')).toContainText('仅「已发货 / 已取消」可删除');

    await expect(
      shippedRow.locator('[data-testid="custom-select"]'),
      '已发货的定制单可删',
    ).not.toHaveClass(/is-disabled/);
    await expect(
      producingRow.locator('[data-testid="custom-select"]'),
      '生产中的定制单不可删（删了审图台与排期会对不上账）',
    ).toHaveClass(/is-disabled/);

    // 服务端兜底：直接调接口删生产中的单 → 删 0 条 + 原因含「进行中」
    const token = await page.evaluate(() => window.localStorage.getItem('sent_user_access_token') ?? '');
    const guard = await request.post(`${API_URL}/custom-orders/batch-delete`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { ids: [producingNo] },
      failOnStatusCode: false,
    });
    const guardBody: any = await guard.json().catch(() => null);
    expect(guardBody?.code, `接口异常：${JSON.stringify(guardBody)}`).toBe(0);
    expect(guardBody?.data?.deleted, '生产中的定制单不能删').toBe(0);
    expect(String(guardBody?.data?.skipped?.[0]?.reason ?? '')).toContain('进行中');
    expect(sql(`SELECT customer_deleted_at IS NULL FROM custom_order WHERE custom_no='${producingNo}'`)).toBe('1');

    // UI 删除已发货那张
    await shippedRow.locator('[data-testid="custom-select"]').click();
    await page.getByTestId('custom-batch-delete').click();
    const box = page.locator('.el-message-box');
    await expect(box).toBeVisible({ timeout: 15_000 });
    await expect(box).toContainText('确定删除选中的 1 张定制单');

    const posted = page.waitForResponse(
      (r) => r.url().includes('/api/custom-orders/batch-delete') && r.request().method() === 'POST',
      { timeout: 20_000 },
    );
    await box.getByRole('button', { name: '删除', exact: true }).click();
    const body: any = await (await posted).json().catch(() => null);
    expect(body?.code).toBe(0);
    expect(body?.data?.deleted).toBe(1);

    await expect(page.locator('.el-message').filter({ hasText: '已删除 1 张定制单' })).toBeVisible({
      timeout: 15_000,
    });
    await expect(
      page.locator('.custom-row').filter({ hasText: shippedNo }),
      '删除后该定制单不应再出现在我的定制列表',
    ).toHaveCount(0, { timeout: 20_000 });
    await expect(page.locator('.custom-row').filter({ hasText: producingNo }), '生产中的单不受影响').toBeVisible();

    expect(sql(`SELECT customer_deleted_at IS NOT NULL FROM custom_order WHERE custom_no='${shippedNo}'`)).toBe('1');
    expect(sql(`SELECT status FROM custom_order WHERE custom_no='${shippedNo}'`), '软删不改状态').toBe('SHIPPED');
  } finally {
    sql(`DELETE FROM custom_order WHERE custom_no IN ('${shippedNo}', '${producingNo}')`);
  }
});
