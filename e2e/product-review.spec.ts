/**
 * 商品评价（V55）：只有买过的人能评价，评完在详情页立刻能看到。
 *
 * 用户诉求：详情页底部要有「用户评价」——好评率、总数、星级、图文列表、九宫格放大；
 * 已购买未评价的人能看到「去评价」，点开填星级/文字/晒图/匿名后提交。
 *
 * 断言链（全程真实读写，不 mock）：
 *   0) 先清掉该商品的历史评价（`clearProductReviews`）——「0 条 / 好评率 100%」是绝对数断言，
 *      不能依赖「刚灌完种子」的库状态；
 *   1) 库里造一笔 9002 的「已完成」订单（纯 SQL，不动库存，避免污染演示数据）；
 *   2) 详情页出现「去评价」→ 弹窗打分 + 填字 + 传 1 张图 + 勾匿名 → 提交成功；
 *   3) 评价列表出现该条、九宫格 1 张图、好评率 100%、按钮变成「您已评价」；
 *   4) 把订单与评价删掉后刷新：按钮消失、列表回到空态（证明资格与列表都读真库）。
 */
import { test, expect, type Page } from '@playwright/test';
import { sql } from './db';
import { pickSellableSku } from './pick-product';

const USER_URL = (process.env.E2E_USER_URL ?? 'http://127.0.0.1:5173').replace(/\/+$/, '');
const API_URL = (process.env.E2E_API_URL ?? 'http://127.0.0.1:8088/api').replace(/\/+$/, '');
const USER_PHONE = process.env.E2E_USER_NAME ?? '13800138000';
const USER_PASSWORD = process.env.E2E_USER_PASSWORD ?? '123456';
const CUSTOMER_ID = 9002;
const STAMP = Date.now().toString().slice(-6);
const ORDER_NO = `RV${STAMP}`;
const NOISE = /Failed to load resource|net::ERR|ERR_NAME_NOT_RESOLVED/i;

async function loginUser(page: Page): Promise<void> {
  await page.goto(`${USER_URL}/login`);
  await page.getByPlaceholder('请输入11位手机号').fill(USER_PHONE);
  await page.getByPlaceholder('请输入密码').fill(USER_PASSWORD);
  // 2026-11-28：登录页新增协议勾选（默认未勾选），E2E 必须先确认协议
  if (await page.getByTestId('auth-agree').count()) {
    await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  }
  await page.getByRole('button', { name: '登录' }).first().click();
  await expect(page, '未登录成功，无法进入详情页评价').not.toHaveURL(/\/login/, { timeout: 20_000 });
}

/** 造一笔已完成订单：只写订单头/行，不碰 sku_stock（用例结束整单删除） */
function seedCompletedOrder(productId: string, skuId: string): void {
  sql(`INSERT INTO orders (order_no, customer_id, status, pay_status, goods_amount_cent, payable_cent, paid_cent)
VALUES ('${ORDER_NO}', ${CUSTOMER_ID}, 'COMPLETED', 'PAID', 100, 100, 100);
SET @oid = LAST_INSERT_ID();
INSERT INTO order_item (order_id, sku_id, qty, unit_price_cent, amount_cent)
VALUES (@oid, ${Number(skuId)}, 1, 100, 100);`);
  // 防御：确认 spu 对得上（资格按 sku.spu_id 判定）
  const spu = sql(`SELECT spu_id FROM sku WHERE id = ${Number(skuId)}`);
  expect(String(spu), `SKU ${skuId} 的 spu_id 必须等于商品 ${productId}`).toBe(String(productId));
}

function cleanup(): void {
  sql(`DELETE FROM product_review WHERE order_no = '${ORDER_NO}';
DELETE oi FROM order_item oi JOIN orders o ON o.id = oi.order_id WHERE o.order_no = '${ORDER_NO}';
DELETE FROM orders WHERE order_no = '${ORDER_NO}';`);
}

/**
 * 评价总数与好评率都是绝对数断言（`0 条` / `1 条` / `100%`），
 * 必须先把该商品的历史评价清干净：上一轮中断留下的残留、以及同商品其它用例
 * 播的种子评价，都会让「0 条」变成「1 条」、「100%」变成别的值。
 * 演示种子不生成评价（DemoDataGenerator 不写 product_review），所以清掉的一定是 E2E 自建的。
 */
function clearProductReviews(productId: string): void {
  sql(`DELETE FROM product_review WHERE product_id = ${Number(productId)}`);
}

/** 详情页的「商品详情 / 用户评价」是真切换（2026-11-22）：断言评价区前必须点到评价面板 */
async function openReviewPanel(page: Page): Promise<void> {
  await page.locator('[data-testid="detail-tab-review"]').click();
  await expect(page.locator('[data-testid="detail-panel-review"]')).toBeVisible({ timeout: 10_000 });
}

test('已购客户在详情页提交图文评价，列表与好评率实时更新', async ({ page }) => {
  test.setTimeout(180_000);
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !NOISE.test(m.text())) errors.push(m.text());
  });

  const picked = await pickSellableSku(page.request, 1);
  // 先清场再断言「0 条」：不依赖「刚灌完种子」的库状态
  clearProductReviews(picked.productId);
  seedCompletedOrder(picked.productId, picked.skuId);

  try {
    await loginUser(page);
    await page.goto(`${USER_URL}/product/${picked.productId}`);

    // 先选中一个可售规格：评价会归属到该 SKU（规格按钮在商品详情面板里，切 tab 后就藏起来了）
    const firstSku = page.locator('.spec-item:not(.disabled)').first();
    await expect(firstSku, '详情页应有可售规格').toBeVisible({ timeout: 20_000 });
    await firstSku.click();

    // 评价在独立面板里：先切 tab，否则它本就不在视口内（2026-11-22 起详情/评价互斥）
    await openReviewPanel(page);
    await expect(page.locator('[data-testid="review-list"]'), '详情页应有用户评价区块')
      .toBeVisible({ timeout: 20_000 });
    const submitBtn = page.locator('[data-testid="review-submit-btn"]');
    await expect(submitBtn, '已完成订单的客户应看到「去评价」').toBeVisible({ timeout: 20_000 });
    await expect(page.locator('[data-testid="review-total"]')).toHaveText('0 条');

    await submitBtn.click();
    // el-dialog 的 data-testid 会落在 overlay 上，按标题定位更稳（弹窗按钮一律 exact 以免误伤）
    const dialog = page.locator('.el-dialog').filter({ hasText: '评价这件商品' });
    await expect(dialog, '点击「去评价」应弹出评价弹窗').toBeVisible({ timeout: 15_000 });

    // 5 星（默认即 5 星，点第 5 颗确保状态确定）+ 文字 + 晒图 + 匿名
    // 注：el-input / el-upload 会把 data-testid 透传到内部 textarea / input，故直接按控件定位
    await dialog.locator('.el-rate__item').nth(4).click();
    await dialog.locator('textarea').fill(`E2E 评价 ${STAMP}：手感扎实，训练够用`);
    await dialog.locator('input[type="file"]').setInputFiles('fixtures/proof.png');
    await dialog.locator('.el-checkbox').click();

    await page.locator('[data-testid="review-dialog-submit"]').click();
    await expect(dialog, '提交成功后弹窗应关闭').toBeHidden({ timeout: 30_000 });

    // 列表：1 条、内容可见、九宫格 1 张图、匿名、好评率 100%
    const firstCard = page.locator('[data-testid="review-item"]').first();
    await expect(firstCard, '提交后列表应立刻出现该评价').toBeVisible({ timeout: 20_000 });
    await expect(firstCard).toContainText(`E2E 评价 ${STAMP}`);
    await expect(firstCard.locator('[data-testid="review-img"]')).toHaveCount(1);
    await expect(firstCard.locator('[data-testid="review-avatar"]')).toHaveText('匿');
    await expect(page.locator('[data-testid="review-total"]')).toHaveText('1 条');
    await expect(page.locator('[data-testid="review-positive-rate"]')).toHaveText('100%');
    await expect(page.locator('[data-testid="review-submit-btn"]'), '已评价后按钮应收起')
      .toHaveCount(0);
    await expect(page.locator('.review__done')).toHaveText('您已评价');

    // 九宫格可放大：点图出现预览层
    await firstCard.locator('[data-testid="review-img"] img').first().click();
    await expect(page.locator('.el-image-viewer__wrapper'), '点击评价图应放大预览')
      .toBeVisible({ timeout: 10_000 });
    await page.keyboard.press('Escape');

    // 删掉本次造的订单与评价 → 列表立刻回空（证明读真库、不是前端缓存）。
    // 注意：9002 在演示数据里有大量历史已完成订单，删掉本单不代表「没买过」，
    // 所以这里只能断言「您已评价」消失 + 列表清空；按钮是否重新出现取决于其它订单。
    cleanup();
    await page.reload();
    await openReviewPanel(page);
    await expect(page.locator('[data-testid="review-total"]'), '评价删除后列表应回到 0 条').toHaveText('0 条');
    await expect(page.locator('.review__done'), '评价已删，不应再显示「您已评价」').toHaveCount(0);

    expect(errors, `控制台不应有业务错误：${errors.join(' | ')}`).toEqual([]);
  } finally {
    cleanup();
  }
});
