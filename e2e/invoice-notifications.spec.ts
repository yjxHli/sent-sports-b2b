/**
 * 开票站内信验收（2026-11-27 用户诉求）。
 *
 * 覆盖四类通知：开具成功 / 标记异常 / 驳回 / 用户端申请被拒。
 * 全程走真实接口 + 演示库 user_message 断言，不靠日志假装发过。
 * 自建自清：临时发票申请与本用例产生的站内信在 finally 里删除，不污染演示账号收件箱。
 */
import { test, expect, type APIRequestContext } from '@playwright/test';
import { sql, sqlNumber, sqlScalar, sqlLit } from './db';

const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');
const CUSTOMER_ID = 9002;
const TITLES = ['发票开具成功', '开票申请被驳回', '发票开具异常', '开票申请被拒'];

async function token(request: APIRequestContext, username: string, aud: string): Promise<string> {
  const res = await request.post(`${API_URL}/auth/login`, {
    data: {
      username, password: '123456', aud,
      deviceId: `e2e-invoice-notify-${Date.now()}-${aud}`,
    },
    failOnStatusCode: false,
  });
  const value = (await res.json().catch(() => null))?.data?.accessToken as string | undefined;
  expect(value, `${aud} 端登录失败`).toBeTruthy();
  return value as string;
}

function messageCount(title: string, afterId: number): number {
  return sqlNumber(`SELECT COUNT(*) FROM user_message WHERE customer_id = ${CUSTOMER_ID}`
    + ` AND title = ${sqlLit(title)} AND id > ${afterId}`);
}

test('开票成功/异常/驳回与用户端申请被拒都会落站内信', async ({ request }) => {
  const admin = await token(request, 'admin', 'admin');
  const adminHeaders = { Authorization: `Bearer ${admin}` };
  const beforeId = sqlNumber(`SELECT COALESCE(MAX(id), 0) FROM user_message WHERE customer_id = ${CUSTOMER_ID}`);

  // 现场造一条待开发票申请：不假设演示库里恰好有 APPLIED 行。
  const created = sqlScalar(`INSERT INTO invoice_request `
    + `(order_id, customer_id, title, tax_no, type, email, status, created_at, updated_at) `
    + `SELECT o.id, o.customer_id, 'E2E 开票通知', NULL, 'NORMAL', NULL, 'APPLIED', NOW(3), NOW(3) `
    + `FROM orders o WHERE o.customer_id = ${CUSTOMER_ID} ORDER BY o.id DESC LIMIT 1; `
    + `SELECT LAST_INSERT_ID();`);
  expect(created, '临时发票申请创建失败').toBeTruthy();
  const invoiceId = Number(created);

  try {
    // 1) 开票成功
    const issued = await request.post(`${API_URL}/admin/invoices/${invoiceId}/issue`, {
      headers: adminHeaders,
      data: { pdfUrl: '', remark: 'E2E 开票成功通知' },
      failOnStatusCode: false,
    });
    expect(issued.ok(), `开票接口失败：${await issued.text()}`).toBeTruthy();
    expect(messageCount('发票开具成功', beforeId), '开票成功必须投递站内信').toBeGreaterThan(0);

    // 2) 驳回（原因原文进正文）；已开发票允许驳回，状态变 REJECTED
    const rejected = await request.post(`${API_URL}/admin/invoices/${invoiceId}/reject`, {
      headers: adminHeaders,
      data: { reason: 'E2E 抬头有误' },
      failOnStatusCode: false,
    });
    expect(rejected.ok(), `驳回失败：${await rejected.text()}`).toBeTruthy();
    expect(messageCount('开票申请被驳回', beforeId), '驳回必须投递站内信').toBeGreaterThan(0);

    // 3) 标记异常（开票失败/异常的运营处置）：REJECTED 也允许再标异常
    const exception = await request.post(`${API_URL}/admin/invoices/${invoiceId}/exception`, {
      headers: adminHeaders,
      data: { reason: 'E2E 税号校验失败' },
      failOnStatusCode: false,
    });
    expect(exception.ok(), `标记异常失败：${await exception.text()}`).toBeTruthy();
    expect(messageCount('发票开具异常', beforeId), '异常原因必须投递站内信').toBeGreaterThan(0);

    // 4) 用户端申请被拒：没有发票行也要通知到客户
    const orderNo = sqlScalar(`SELECT o.order_no FROM orders o `
      + `WHERE o.customer_id = ${CUSTOMER_ID} AND o.status <> 'COMPLETED' `
      + `ORDER BY o.id DESC LIMIT 1`);
    expect(orderNo, '演示库应有未完成订单用于申请被拒').toBeTruthy();
    const user = await token(request, '13800138000', 'user');
    const applied = await request.post(`${API_URL}/invoices`, {
      headers: { Authorization: `Bearer ${user}` },
      data: { orderNo, title: 'E2E 申请被拒', type: 'NORMAL' },
      failOnStatusCode: false,
    });
    expect(applied.ok(), `被拒场景不应返回成功：${await applied.text()}`).toBeFalsy();
    expect(messageCount('开票申请被拒', beforeId), '用户端申请被拒必须投递站内信').toBeGreaterThan(0);
  } finally {
    const titles = TITLES.map(sqlLit).join(',');
    sql(`DELETE FROM user_message WHERE customer_id = ${CUSTOMER_ID}`
      + ` AND id > ${beforeId} AND title IN (${titles});`);
    sql(`DELETE FROM invoice_request WHERE id = ${invoiceId}`);
  }
});
