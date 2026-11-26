/**
 * 用户自助删除「我的优惠券」里已使用/已过期的券（2026-11-26 用户需求，TDD 先红后绿）。
 *
 * 验收口径：
 *   - 待使用券拒绝删除（40901）；管理端令牌不能走用户删除端点（40101）；
 *   - 已使用 / 已过期券可以删除，前端卡片与 tab 计数同步减少，库里只打 deleted；
 *   - 重复删除幂等成功。
 *
 * 本用例自建两张券并物理清理，不依赖演示库既有券。
 */
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { test, expect, type Page } from '@playwright/test';
import {
  USER_URL, join, apiCall, loginApi,
  ADMIN_NAME, ADMIN_PASSWORD, CUSTOMER_PHONE, CUSTOMER_PASSWORD, CUSTOMER_ID,
} from './chat-helpers';

const LOCAL_MYSQL = 'D:/MySQL/MySQL Server 8.0/bin/mysql.exe';
const REASON_USED = `E2E删券-used-${Date.now()}`;
const REASON_EXPIRED = `E2E删券-expired-${Date.now()}`;
const createdNos: string[] = [];

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

function cleanup(): void {
  if (!createdNos.length) return;
  const list = createdNos.map((n) => `'${n}'`).join(',');
  try {
    sql(`DELETE FROM customer_coupon WHERE coupon_no IN (${list});`);
    sql(`DELETE FROM user_message WHERE biz_no IN (${list});`);
  } catch {
    // 没有本机 mysql 时不阻塞用例结论
  }
}

async function loginUserUi(page: Page): Promise<void> {
  await page.goto(join(USER_URL, '/login'));
  await page.getByPlaceholder('请输入11位手机号').fill(CUSTOMER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(CUSTOMER_PASSWORD);
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

test('我的优惠券：已用/过期可删，待用与管理端不可删，重复删除幂等', async ({ page, request }) => {
  test.setTimeout(240_000);
  try {
    const adminToken = await loginApi(request, ADMIN_NAME, ADMIN_PASSWORD, 'admin');
    const userToken = await loginApi(request, CUSTOMER_PHONE, CUSTOMER_PASSWORD, 'user');
    const templates = await apiCall(request, 'GET', '/admin/coupons/templates', undefined, adminToken);
    const template = (templates?.data ?? []).find((t: any) => Number(t.amountCent) === 500);
    expect(template, '应有 5 元券档位').toBeTruthy();

    const issued: any[] = [];
    for (const reason of [REASON_USED, REASON_EXPIRED]) {
      const res = await apiCall(request, 'POST', '/admin/coupons/issue', {
        templateId: template.templateId, customerIds: [CUSTOMER_ID],
        thresholdCent: 0, reason, scene: 'ACTIVITY',
      }, adminToken);
      expect(res?.code, `发券失败：${JSON.stringify(res)}`).toBe(0);
      issued.push(res);
    }

    const mineRes = await apiCall(request, 'GET', '/coupons/mine', undefined, userToken);
    const mine: any[] = mineRes?.data ?? [];
    const used = mine.find((c) => c.reason === REASON_USED);
    const expired = mine.find((c) => c.reason === REASON_EXPIRED);
    expect(used && expired, '两张券都应出现在我的优惠券').toBeTruthy();
    createdNos.push(String(used.couponNo), String(expired.couponNo));

    // 未使用券拒绝；管理端令牌不能消费用户端点
    const unused = await apiCall(request, 'DELETE', `/coupons/${used.couponId}`, undefined, userToken);
    expect(unused?.code, '待使用券不能删').toBe(40901);
    const asAdmin = await apiCall(request, 'DELETE', `/coupons/${used.couponId}`, undefined, adminToken);
    expect(asAdmin?.code, '管理端令牌不能走用户删除端点').toBe(40101);

    // 造一张已使用、一张已过期（过期态由 expire_at 派生，库里仍是 UNUSED）
    sql(`UPDATE customer_coupon SET status = 'USED', used_at = NOW(3), `
      + `order_no = 'E2E-${used.couponNo}' WHERE coupon_no = '${used.couponNo}';`);
    sql(`UPDATE customer_coupon SET expire_at = DATE_SUB(NOW(3), INTERVAL 1 DAY) `
      + `WHERE coupon_no = '${expired.couponNo}';`);

    await loginUserUi(page);
    await page.goto(join(USER_URL, '/coupons'));

    for (const [tab, reason] of [
      ['已使用', REASON_USED], ['已过期', REASON_EXPIRED],
    ] as const) {
      await page.locator('.coupon-tab', { hasText: tab }).click();
      const card = page.getByTestId('coupon-card').filter({ hasText: reason });
      await expect(card, `${tab} tab 应能看到这张券`).toBeVisible({ timeout: 20_000 });
      await card.getByTestId('coupon-delete-btn').click();
      const dialog = page.locator('.el-message-box');
      await expect(dialog, '删除必须二次确认').toBeVisible({ timeout: 10_000 });
      await dialog.getByRole('button', { name: '确定' }).click();
      await expect(card, `${tab} 删除后卡片应从列表消失`).toHaveCount(0, { timeout: 20_000 });
      expect(sql(`SELECT deleted FROM customer_coupon WHERE coupon_no = '${
        reason === REASON_USED ? used.couponNo : expired.couponNo}';`)).toBe('1');
    }

    // 重复删除幂等：同一张券再删一次仍然成功
    const again = await apiCall(request, 'DELETE', `/coupons/${used.couponId}`, undefined, userToken);
    expect(again?.code, '重复删除应幂等成功').toBe(0);
  } finally {
    cleanup();
  }
});
