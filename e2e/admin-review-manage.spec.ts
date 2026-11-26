/**
 * 管理端「评价管理」（V56）：运营下架违规评价的完整闭环 + 权限隔离。
 *
 * 断言链（真实读写，不 mock）：
 *   1) 用 SQL 播两条评价（5 星带图 / 2 星无图），独立 order_no，用例结束按 order_no 精确清理；
 *      播种前先清掉该商品的历史评价（`clearProductReviews`），否则上一轮残留会把「2 条」打成 3 条；
 *   2) admin 登录 → 商品 → 评价管理：按商品 ID 筛选能看到这两条，商品标题/脱敏用户/星级/状态都在；
 *   3) 「查看详情」弹窗展示完整正文与九宫格，点图能放大；
 *   4) 「隐藏」→ 行内状态变「已隐藏」，用户端 GET /api/products/{id}/reviews 查不到该条、
 *      total 与好评率同步重算，sys_operation_log 留一条 HIDE；
 *   5) 「显示」→ 用户端恢复可见；
 *   6) 权限隔离：持 '*' 的销售账号看不到菜单、直接访问落 403、调接口返回 40301。
 */
import { test, expect, type Page } from '@playwright/test';
import { sql, sqlLit } from './db';
import { pickSellableSku } from './pick-product';

const ADMIN_URL = (process.env.E2E_ADMIN_URL ?? 'http://127.0.0.1:5174/admin/').replace(/\/+$/, '');
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');
/** 管理端 base 已含 /admin，页面路径一律 `/xxx`（直接拼 'login' 会得到 /adminlogin） */
const adminPath = (path: string): string => `${ADMIN_URL}/${path.replace(/^\/+/, '')}`;
const STAMP = Date.now().toString().slice(-6);
const GOOD_ORDER_NO = `ARG${STAMP}`;
const BAD_ORDER_NO = `ARB${STAMP}`;
const GOOD_CONTENT = `E2E 治理好评 ${STAMP}：面料透气，尺码标准`;
const BAD_CONTENT = `E2E 治理差评 ${STAMP}：走线有瑕疵，客服也联系不上`;

/** 用 admin API 登录拿 token：仅用于「上传一张真实评价图」，UI 登录另走 login() */
async function adminToken(page: Page): Promise<string> {
  const res = await page.request.post(`${API_URL}/auth/login`, {
    data: { username: 'admin', password: '123456', aud: 'admin', deviceId: 'e2e-review-seed' },
  });
  const token = (await res.json())?.data?.accessToken;
  expect(token, 'admin 应能登录（否则无法上传评价图）').toBeTruthy();
  return token;
}

/** 上传一张真实存在的图，返回文件服务 fileId（评价图走 /api/files/{fileId}，假 id 会渲染成「加载失败」） */
async function uploadProof(page: Page, token: string): Promise<string> {
  const { readFileSync } = await import('node:fs');
  const res = await page.request.post(`${API_URL}/files`, {
    headers: { Authorization: `Bearer ${token}` },
    multipart: {
      file: { name: 'proof.png', mimeType: 'image/png', buffer: readFileSync('fixtures/proof.png') },
    },
  });
  const body = await res.json();
  expect(body?.data?.fileId, `评价图上传失败：${JSON.stringify(body)}`).toBeTruthy();
  return body.data.fileId;
}

function seedReview(orderNo: string, productId: string, skuId: string, rating: number,
                    content: string, imagesJson: string): string {
  sql(`INSERT INTO product_review (product_id, sku_id, order_no, customer_id, rating, content, images_json) `
    + `VALUES (${Number(productId)}, ${Number(skuId)}, '${orderNo}', 9002, ${rating}, ${sqlLit(content)}, ${sqlLit(imagesJson)})`);
  return sql(`SELECT id FROM product_review WHERE order_no = '${orderNo}'`);
}

/**
 * 列表断言是绝对数（按商品筛选「正好 2 条」、隐藏后用户端 total=1、好评率 100%），
 * 所以先把该商品的历史评价清干净：上一轮中断留下的残留会把 2 打成 3，
 * 也会让「隐藏后只剩 1 条好评」的口径失真。演示种子不生成评价，清掉的一定是 E2E 自建的。
 */
function clearProductReviews(productId: string): void {
  sql(`DELETE FROM product_review WHERE product_id = ${Number(productId)}`);
}

function cleanup(reviewIds: string[]): void {
  sql(`DELETE FROM product_review WHERE order_no IN ('${GOOD_ORDER_NO}', '${BAD_ORDER_NO}')`);
  if (reviewIds.length) {
    const ids = reviewIds.map((id) => `'${id}'`).join(',');
    sql(`DELETE FROM sys_operation_log WHERE module = 'REVIEW' AND target_id IN (${ids})`);
  }
}

async function login(page: Page, username: string): Promise<void> {
  await page.goto(adminPath('login'));
  await expect(page.getByPlaceholder('用户名')).toBeVisible({ timeout: 15_000 });
  await page.getByPlaceholder('用户名').fill(username);
  await page.getByPlaceholder('密码').fill('123456');
  await Promise.all([
    page.waitForResponse((r) => r.url().includes('/api/auth/login') && r.request().method() === 'POST'),
    page.getByRole('button', { name: /登\s*录/ }).click(),
  ]);
  await expect(page, `${username} 未登录成功`).not.toHaveURL(/\/login/, { timeout: 20_000 });
}

/** 用户端评价接口：不登录也能读，返回 summary + list + total */
async function userReviewPage(page: Page, productId: string): Promise<any> {
  const res = await page.request.get(`${API_URL}/products/${productId}/reviews?page=1&size=50`);
  expect(res.status()).toBe(200);
  return (await res.json()).data;
}

// 数据强依赖：本文件内的用例共享演示库状态（购物车/库存/券/发票/会话/配置），
// 必须按书写顺序在同一个 worker 里跑完，禁止拆到多 worker 并行。
test.describe.configure({ mode: 'serial' });

test('评价管理：筛选 → 看详情 → 隐藏（用户端消失/好评率重算/留痕）→ 恢复', async ({ page }) => {
  test.setTimeout(180_000);
  const picked = await pickSellableSku(page.request, 1);
  // 先清掉该商品的历史评价，「按商品筛选正好 2 条」才是确定的
  clearProductReviews(picked.productId);
  // 评价图必须是文件服务里真实存在的 fileId，否则表格里是「加载失败」，也就无法验证放大预览
  const token = await adminToken(page);
  const imageFileId = await uploadProof(page, token);
  const goodId = seedReview(GOOD_ORDER_NO, picked.productId, picked.skuId, 5, GOOD_CONTENT,
    JSON.stringify([imageFileId]));
  const badId = seedReview(BAD_ORDER_NO, picked.productId, picked.skuId, 2, BAD_CONTENT, '[]');

  try {
    await login(page, 'admin');
    await page.goto(adminPath('product/reviews'));
    await expect(page.getByRole('heading', { name: '评价管理' })).toBeVisible({ timeout: 20_000 });

    // 1) 按商品 ID 筛选（el-input 会把 data-testid 透传到内部 input 上，不要再找一层 input）
    await page.locator('[data-testid="admin-review-filter-product"]').fill(picked.productId);
    await page.locator('[data-testid="admin-review-query"]').click();

    const rows = page.locator('[data-testid="admin-review-list"] .el-table__body-wrapper tbody tr');
    await expect(rows, '按商品筛选后应能看到刚播种的两条评价').toHaveCount(2, { timeout: 20_000 });

    const goodRow = rows.filter({ hasText: GOOD_CONTENT });
    const badRow = rows.filter({ hasText: BAD_CONTENT });
    // 昵称脱敏后是「首字 + **」，具体首字取决于演示客户昵称，不写死
    await expect(goodRow).toContainText('**');
    await expect(badRow).toContainText('**');
    await expect(goodRow).toContainText('正常');
    await expect(goodRow.locator('[data-testid="admin-review-img"]')).toHaveCount(1);

    // 2) 查看详情：完整正文 + 九宫格大图可放大
    await goodRow.locator('[data-testid="admin-review-detail-btn"]').click();
    const dialog = page.locator('.el-dialog').filter({ hasText: '评价详情' });
    await expect(dialog, '点击查看详情应弹出评价详情').toBeVisible({ timeout: 10_000 });
    await expect(dialog).toContainText(GOOD_CONTENT);
    await dialog.locator('[data-testid="admin-review-img"] img').first().click();
    await expect(page.locator('.el-image-viewer__wrapper'), '评价图应能放大预览').toBeVisible({ timeout: 10_000 });
    await page.keyboard.press('Escape');
    // 弹窗右上角还有一个「关闭此对话框」图标按钮，必须 exact 才能唯一命中底部按钮
    await dialog.getByRole('button', { name: '关闭', exact: true }).click();
    await expect(dialog).toBeHidden({ timeout: 10_000 });

    // 3) 隐藏差评
    await badRow.locator('[data-testid="admin-review-hide-btn"]').click();
    await page.locator('.el-popconfirm').getByRole('button', { name: '确定' }).click();
    await expect(badRow.locator('[data-testid="admin-review-status"]'), '隐藏后行内状态应变成已隐藏')
      .toHaveText('已隐藏', { timeout: 20_000 });

    // 4) 用户端：查不到、总数与好评率重算（后端 Long 序列化成字符串，全部 Number() 后再比）
    const afterHide = await userReviewPage(page, picked.productId);
    expect(Number(afterHide.total)).toBe(1);
    expect(afterHide.list.map((r: any) => r.content)).toContain(GOOD_CONTENT);
    expect(afterHide.list.map((r: any) => r.content)).not.toContain(BAD_CONTENT);
    expect(Number(afterHide.summary.total)).toBe(1);
    expect(Number(afterHide.summary.positiveRate)).toBe(100);
    expect(Number(afterHide.summary.negativeCount)).toBe(0);

    // 5) 操作日志留痕
    const log = sql(`SELECT module, action, target_id FROM sys_operation_log `
      + `WHERE module = 'REVIEW' AND target_id = '${badId}' ORDER BY id DESC LIMIT 1`);
    expect(log).toContain('HIDE');
    expect(log).toContain(String(badId));

    // 6) 状态筛选 + 恢复：恢复后该行不再满足「已隐藏」条件，列表应自动回到空
    await page.locator('[data-testid="admin-review-filter-status"]').click();
    await page.getByRole('option', { name: '已隐藏' }).click();
    await page.locator('[data-testid="admin-review-query"]').click();
    await expect(rows, '按「已隐藏」筛选应只剩这一条').toHaveCount(1, { timeout: 20_000 });
    await rows.first().locator('[data-testid="admin-review-show-btn"]').click();
    await Promise.all([
      page.waitForResponse((r) => r.url().includes('/api/admin/reviews/') && r.request().method() === 'PUT'),
      page.locator('.el-popconfirm').getByRole('button', { name: '确定' }).click(),
    ]);
    await expect(rows, '恢复后不再命中「已隐藏」筛选').toHaveCount(0, { timeout: 20_000 });

    // 切到「正常」筛选，确认那条差评回来了且状态是「正常」
    await page.locator('[data-testid="admin-review-filter-status"]').click();
    await page.getByRole('option', { name: '正常' }).click();
    await page.locator('[data-testid="admin-review-query"]').click();
    const badRowRestored = page.locator('[data-testid="admin-review-list"] .el-table__body-wrapper tbody tr')
      .filter({ hasText: BAD_CONTENT });
    await expect(badRowRestored, '恢复后差评应重新出现在「正常」筛选里').toHaveCount(1, { timeout: 20_000 });
    await expect(badRowRestored.locator('[data-testid="admin-review-status"]')).toHaveText('正常');

    const afterRestore = await userReviewPage(page, picked.productId);
    expect(Number(afterRestore.total)).toBe(2);
    expect(Number(afterRestore.summary.negativeCount)).toBeGreaterThanOrEqual(1);
  } finally {
    cleanup([goodId, badId]);
  }
});

test('权限隔离：持 * 的销售账号看不到评价管理菜单，接口直接 40301', async ({ page }) => {
  test.setTimeout(120_000);
  await login(page, 'sales');
  await expect(page.locator('.sent-menu'), '销售账号不应看到「评价管理」菜单')
    .not.toContainText('评价管理');

  await page.goto(adminPath('product/reviews'));
  await expect(page, '销售账号直接访问应落到无权限页').toHaveURL(/\/error\/403/, { timeout: 15_000 });

  // 后端二次校验：带销售 token 调接口必须 40301，不能靠前端藏菜单
  const loginRes = await page.request.post(`${API_URL}/auth/login`, {
    data: { username: 'sales', password: '123456', aud: 'admin', deviceId: 'e2e-review-perm' },
  });
  const token = (await loginRes.json())?.data?.accessToken;
  expect(token, '销售账号应能正常登录（否则本用例失去意义）').toBeTruthy();
  const res = await page.request.get(`${API_URL}/admin/reviews?page=1&size=10`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  const body = await res.json();
  expect(body.code, `评价管理接口必须拒绝销售账号，实际返回：${JSON.stringify(body)}`).toBe(40301);
});
