/**
 * 商城端个人中心（2026-11-27 用户要求，参考淘宝「我的淘宝」）。
 *
 * 断言口径：
 *   1) 顶部一行「森特 | 我的森特」+ 右侧搜索框，下方左侧导航八项齐全（含子项）；
 *   2) 概览页一行四卡（我的订单 / 我的收藏 / 我的足迹 / 购物车）真的并排，
 *      资料卡右侧两块是「可用积分 + 我的优惠券」；
 *   3) 我的发票三个页签都走真接口（ISSUED / OPEN / pending），切页签后表格或空态必须出现；
 *   4) 非企业账户进「企业工具」看到「资质不够 + 去申请企业认证」，点击进入企业认证页并可上传营业执照。
 *
 * 只读用例：不写库、不改数据，放默认 parallel 桶。
 */
import { test, expect, type Page } from '@playwright/test';
import { USER_URL, join, CUSTOMER_PHONE, CUSTOMER_PASSWORD } from './chat-helpers';

/** 演示库里的另一张个人账户（CUS-20001，会员1）：13800138000 已被开通企业认证，不能再用它验「资质不够」 */
const PERSONAL_ONLY_PHONE = '18600091004';

async function login(page: Page, phone = CUSTOMER_PHONE, password = CUSTOMER_PASSWORD): Promise<void> {
  await page.goto(join(USER_URL, '/login'));
  await page.getByPlaceholder('请输入11位手机号').fill(phone);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.getByTestId('auth-agree').locator('.el-checkbox__inner').click();
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/(home|products)(\?|$)/, { timeout: 20_000 });
}

test('个人中心：顶部标识、搜索框与侧边导航齐全', async ({ page }) => {
  await login(page);
  await page.goto(join(USER_URL, '/mine'));

  await expect(page.getByTestId('mine-brand')).toHaveText('森特', { timeout: 20_000 });
  await expect(page.getByTestId('mine-title')).toHaveText('我的森特');
  await expect(page.getByTestId('mine-search-input')).toBeVisible();
  await expect(page.getByTestId('mine-search-btn')).toBeVisible();

  const sidebar = page.getByTestId('mine-sidebar');
  await expect(sidebar).toBeVisible();

  // 八个一级入口（2026-11-28 起「会员中心」也从顶栏挪进来了）
  for (const label of ['我的森特', '我的购物车', '订单中心', '我的优惠券', '足迹收藏', '我的发票', '我的售后', '会员中心', '企业工具']) {
    await expect(sidebar, `侧边导航缺少「${label}」`).toContainText(label);
  }

  // 分组**默认全部收起**（2026-11-28 用户要求：需要手动点开才展开）
  await expect(sidebar.locator('.me-nav__children'), '分组默认收起时不应渲染子项').toHaveCount(0);

  // 点开「订单中心」：子项出现，且包含新的「评价中心」
  await page.getByTestId('mine-nav-orders').click();
  await expect(page.getByTestId('mine-nav-orders-goods'), '展开订单中心应看到商品订单').toBeVisible();
  await expect(page.getByTestId('mine-nav-orders-custom'), '展开订单中心应看到定制订单').toBeVisible();
  await expect(page.getByTestId('mine-nav-orders-reviews'), '订单中心下应有评价中心').toBeVisible();

  // 点开「足迹收藏」与「企业工具」，子项齐全
  await page.getByTestId('mine-nav-trace').click();
  for (const label of ['我的足迹', '商品收藏']) {
    await expect(sidebar, `侧边导航缺少「${label}」`).toContainText(label);
  }
  await page.getByTestId('mine-nav-enterprise').click();
  for (const label of ['企业采购', '我的授信', '先采后付', '我的合同', '团购专区']) {
    await expect(sidebar, `侧边导航缺少「${label}」`).toContainText(label);
  }

  // 首页挪过来的「团购专区」入口：与其他企业工具子项一样同页跳转，且落地页仍有侧边栏
  const groupBuy = page.getByTestId('mine-nav-group-buy');
  await expect(groupBuy).not.toHaveAttribute('target', '_blank');
  await groupBuy.click();
  await expect(page).toHaveURL(/\/group-buy(\?|$)/, { timeout: 20_000 });
  await expect(page.getByTestId('mine-sidebar'), '团购专区应保留我的森特侧边栏').toBeVisible();
  await page.goto(join(USER_URL, '/mine'));

  // 概览资料卡仍是真数据（顶栏账户用例依赖 .profile-name）
  await expect(page.locator('.profile-name')).not.toBeEmpty();
  await expect(page.getByText('我的订单', { exact: true }).first()).toBeVisible();

  // 资料卡右侧两块：可用积分 + 我的优惠券（2026-11-27 用户要求换掉成长值）
  const side = page.locator('.profile-side');
  await expect(side).toContainText('可用积分');
  await expect(side).toContainText('我的优惠券');
  await expect(side.locator('.side-mini')).toHaveCount(2);
  await expect(side.locator('.side-mini__num').nth(1)).toContainText('张');

  // 一行四卡：我的订单 / 我的收藏 / 我的足迹 / 购物车 必须并排（同一行，纵向起点一致）
  const row = page.locator('.home-row');
  await expect(row).toBeVisible();
  const cards = row.locator(':scope > .home-card');
  await expect(cards, '概览应有一行四卡').toHaveCount(4);
  for (const label of ['我的订单', '我的收藏', '我的足迹', '购物车']) {
    await expect(cards.filter({ hasText: label }), `四卡里缺「${label}」`).toHaveCount(1);
  }
  const tops = await cards.evaluateAll((els) => els.map((el) => el.getBoundingClientRect().top));
  expect(Math.max(...tops) - Math.min(...tops), `四卡应并排在同一行，实际 top=${tops.join('/')}`)
    .toBeLessThan(2);
  // 订单卡底部有最近一笔 / 占位文案
  await expect(page.locator('.orders-hint')).not.toBeEmpty();

  // 顶部搜索框与商城首页同款：搜索结果新开标签页，个人中心原页不动
  await page.getByTestId('mine-search-input').fill('哑铃');
  const popup = page.waitForEvent('popup', { timeout: 15_000 });
  await page.getByTestId('mine-search-btn').click();
  const resultTab = await popup;
  await expect(resultTab).toHaveURL(/\/products\?keyword=/, { timeout: 15_000 });
  await expect(page, '搜索不应把个人中心原页挤走').toHaveURL(/\/mine(\?|$)/);
  await resultTab.close();

  // 资料卡里的「领券中心」入口也新开标签（与首页「立即领取」同款）
  const couponLink = page.locator('.side-coupon');
  await expect(couponLink, '应有领券中心入口').toBeVisible();
  const couponPopup = page.waitForEvent('popup', { timeout: 15_000 });
  await couponLink.click();
  const couponTab = await couponPopup;
  await expect(couponTab).toHaveURL(/\/coupon\/center(\?|$)/, { timeout: 15_000 });
  await couponTab.close();
  await expect(page, '点领券中心不应把个人中心原页挤走').toHaveURL(/\/mine(\?|$)/);

  // 回到个人中心，点侧边栏「我的发票」进入个人中心内的发票页
  await page.goto(join(USER_URL, '/mine'));
  await page.getByTestId('mine-nav-invoices').click();
  await expect(page).toHaveURL(/\/mine\/invoices(\?|$)/, { timeout: 15_000 });
  await expect(page.getByTestId('mine-title')).toHaveText('我的发票');
});

/**
 * 2026-11-28 用户要求的一批改动：
 *   - 概览底部四个工具按钮（在线客服 / 我的定制 / 退款售后 / 退出登录）删掉；
 *   - 收藏 / 足迹 / 购物车卡只展示最新 1 件；
 *   - 「待评价」点进去是评价中心（不是订单列表）；
 *   - 评价中心只展示「已写评论 / 浏览数 / 获赞数」三项；
 *   - 个人中心搜索框有历史记录（与首页共用一份）。
 */
test('个人中心：订单五状态整体下移，查看全部订单居中且在下方', async ({ page }) => {
  await login(page);
  await page.goto(join(USER_URL, '/mine'));

  const card = page.locator('.orders-panel');
  await expect(card).toBeVisible({ timeout: 20_000 });
  const title = card.locator('.panel-head h3');
  const stats = card.locator('.order-stat');
  const more = card.locator('.panel-more');

  await expect(stats, '订单五状态应有五项').toHaveCount(5);
  const titleBox = await title.boundingBox();
  const statBoxes = [];
  for (let i = 0; i < 5; i++) statBoxes.push(await stats.nth(i).boundingBox());
  expect(statBoxes.every(Boolean)).toBe(true);
  const statTop = Math.min(...statBoxes.map((b) => b!.y));
  const statBottom = Math.max(...statBoxes.map((b) => b!.y + b!.height));

  // 数字与文字整体下移：与标题留出更大间距
  expect(statTop - (titleBox!.y + titleBox!.height), '订单五状态应整体下移')
    .toBeGreaterThanOrEqual(20);

  // 「查看全部订单」放在五项下方，并且水平居中
  const moreBox = await more.boundingBox();
  const cardBox = await card.boundingBox();
  expect(moreBox!.y, '查看全部订单应在五个状态下方').toBeGreaterThanOrEqual(statBottom);
  expect(
    Math.abs((moreBox!.x + moreBox!.width / 2) - (cardBox!.x + cardBox!.width / 2)),
    '查看全部订单应居中',
  ).toBeLessThan(6);
});

test('个人中心：猜你喜欢商品卡与更多好物都新开标签', async ({ page }) => {
  await login(page);
  await page.goto(join(USER_URL, '/mine'));

  const card = page.locator('.like-grid .good-card').first();
  await expect(card, '猜你喜欢应有商品卡').toBeVisible({ timeout: 20_000 });
  expect(await card.evaluate((el) => el.tagName)).toBe('A');
  await expect(card).toHaveAttribute('target', '_blank');
  await expect(card).toHaveAttribute('href', /\/product\/\w+/);
  const detailPopup = page.waitForEvent('popup', { timeout: 20_000 });
  await card.click();
  const detail = await detailPopup;
  await expect(detail).toHaveURL(/\/product\/\w+/, { timeout: 20_000 });
  expect(page.url(), '点猜你喜欢后个人中心原页不应导航走').toContain('/mine');
  await detail.close();

  const more = page.locator('.like-panel .panel-more');
  await expect(more).toHaveAttribute('target', '_blank');
  const morePopup = page.waitForEvent('popup', { timeout: 20_000 });
  await more.click();
  const products = await morePopup;
  await expect(products).toHaveURL(/\/products(\?|$)/, { timeout: 20_000 });
  expect(page.url(), '点更多好物后个人中心原页不应导航走').toContain('/mine');
  await products.close();
});

test('个人中心：待评价跳评价中心、底部工具已移除、三卡只留 1 件', async ({ page }) => {
  await login(page);
  await page.goto(join(USER_URL, '/mine'));
  await expect(page.locator('.mine-page'), '个人中心应渲染').toBeVisible({ timeout: 20_000 });

  // 1) 底部四个工具按钮已删除（顶栏账户下拉里还有「退出登录」，所以只查概览区）
  await expect(page.locator('.mine-tools'), '概览底部工具区应已删除').toHaveCount(0);
  await expect(page.locator('.mine-page .mine-tool'), '概览不应再有工具按钮').toHaveCount(0);

  // 2) 三张小卡最多只露 1 件
  for (const label of ['我的收藏', '我的足迹']) {
    const panel = page.locator('.mini-panel').filter({ hasText: label });
    await expect.poll(async () => panel.locator('.mini-item').count(), {
      timeout: 20_000,
      message: `${label}卡应只展示最新 1 件`,
    }).toBeLessThanOrEqual(1);
  }

  // 3) 「待评价」直接进评价中心
  const pendingReview = page.locator('.order-stat').filter({ hasText: '待评价' });
  await expect(pendingReview, '订单区应有「待评价」').toBeVisible();
  await pendingReview.click();
  await expect(page).toHaveURL(/\/mine\/reviews(\?|$)/, { timeout: 20_000 });

  // 4) 评价中心：三项统计 + 列表或空态
  await expect(page.getByTestId('mine-title')).toHaveText('评价中心');
  for (const key of ['written', 'views', 'likes']) {
    await expect(page.getByTestId(`review-stat-${key}`), `评价中心缺少统计项 ${key}`).toBeVisible();
  }
  await expect(page.getByTestId('review-stat-written')).toContainText('已写评论');
  await expect(page.getByTestId('review-stat-views')).toContainText('浏览数');
  await expect(page.getByTestId('review-stat-likes')).toContainText('获赞数');
  // 默认落在「待评价」页签，所以列表项是 pending-review-item；切到「已评价」才是 review-item
  await expect(
    page
      .getByTestId('pending-review-item')
      .first()
      .or(page.getByTestId('review-item').first())
      .or(page.getByTestId('reviews-empty')),
    '评价中心要有列表或空态',
  ).toBeVisible({ timeout: 20_000 });
});

test('个人中心：搜索框带历史记录（与首页同款）', async ({ page }) => {
  await login(page);
  await page.goto(join(USER_URL, '/mine'));

  const input = page.getByTestId('mine-search-input');
  await expect(input).toBeVisible({ timeout: 20_000 });

  // 先搜一次，让历史里有词（同样新开标签页）
  await input.fill('篮球');
  const popup = page.waitForEvent('popup', { timeout: 15_000 });
  await page.getByTestId('mine-search-btn').click();
  const resultTab = await popup;
  await expect(resultTab).toHaveURL(/\/products\?keyword=/, { timeout: 15_000 });
  await resultTab.close();

  await page.goto(join(USER_URL, '/mine'));
  await input.click();
  const panel = page.getByTestId('mine-search-history');
  await expect(panel, '聚焦搜索框应弹出历史记录').toBeVisible({ timeout: 10_000 });
  await expect(page.getByTestId('mine-history-item').filter({ hasText: '篮球' }).first(),
    '历史里应有刚才搜过的词').toBeVisible();

  // 清除历史后回到「暂无历史搜索」
  await page.getByTestId('mine-clear-history').click();
  await expect(panel).toContainText('暂无历史搜索');
});

test('我的发票：已开具 / 申请中 / 未申请三个页签都走真接口', async ({ page }) => {  await login(page);
  const errors: string[] = [];
  page.on('console', (m) => {
    if (m.type() === 'error' && !/Failed to load resource|net::ERR/i.test(m.text())) {
      errors.push(m.text());
    }
  });

  const issuedResp = page.waitForResponse(
    (r) => r.request().method() === 'GET' && r.url().includes('/api/invoices?') && r.url().includes('status=ISSUED'),
    { timeout: 25_000 },
  );
  await page.goto(join(USER_URL, '/mine/invoices'));
  expect((await issuedResp).status()).toBe(200);

  await expect(page.getByTestId('invoice-tab-issued')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId('invoice-tab-open')).toBeVisible();
  await expect(page.getByTestId('invoice-tab-pending')).toBeVisible();
  await expect(page.getByTestId('invoice-search')).toBeVisible();

  // 已开具：有行或空态都算正常，但必须渲染出结果（不能一直 loading）
  await expect(
    page.getByTestId('invoice-row').first().or(page.getByTestId('invoice-empty')),
  ).toBeVisible({ timeout: 20_000 });
  if (await page.getByTestId('invoice-row').count()) {
    const head = page.locator('.inv-table thead');
    await expect(head).toContainText('开票金额');
    await expect(head).toContainText('开票进度');
    await expect(page.getByTestId('invoice-row').first()).toContainText('订单号');
  }

  // 申请中
  const openResp = page.waitForResponse(
    (r) => r.request().method() === 'GET' && r.url().includes('/api/invoices?') && r.url().includes('status=OPEN'),
    { timeout: 20_000 },
  );
  await page.getByTestId('invoice-tab-open').click();
  expect((await openResp).status()).toBe(200);
  await expect(
    page.getByTestId('invoice-row').first().or(page.getByTestId('invoice-empty')),
  ).toBeVisible({ timeout: 20_000 });

  // 未申请：走 /invoices/pending，搜索框隐藏（订单列表没有发票号可搜）
  const pendingResp = page.waitForResponse(
    (r) => r.request().method() === 'GET' && r.url().includes('/api/invoices/pending'),
    { timeout: 20_000 },
  );
  await page.getByTestId('invoice-tab-pending').click();
  expect((await pendingResp).status()).toBe(200);
  await expect(
    page.getByTestId('pending-row').first().or(page.getByTestId('invoice-empty')),
  ).toBeVisible({ timeout: 20_000 });
  await expect(page.getByTestId('invoice-search')).toHaveCount(0);
  expect(errors, '发票页不应有 console 错误').toHaveLength(0);
});

test('企业工具：企业采购页内的团购推广卡新开标签', async ({ page }) => {
  await login(page);
  await page.goto(join(USER_URL, '/mine/enterprise/procurement'));

  const card = page.locator('.ent-groupbuy');
  await expect(card, '首页挪来的企业采购推广卡应出现在企业工具里').toBeVisible({ timeout: 20_000 });
  await expect(card).toContainText('企业采购');
  await expect(card).toContainText('专票');

  const link = card.getByRole('link', { name: /查看团购/ });
  await expect(link, '团购入口必须新开标签').toHaveAttribute('target', '_blank');
  const popup = page.waitForEvent('popup', { timeout: 15_000 });
  await link.click();
  const tab = await popup;
  await expect(tab).toHaveURL(/\/group-buy(\?|$)/, { timeout: 20_000 });
  await expect(tab.getByTestId('mine-sidebar'), '新开的团购专区也应有侧边栏').toBeVisible();
  await tab.close();
  expect(page.url(), '原企业采购页不应导航走').toContain('/mine/enterprise/procurement');
});

test('企业工具：非企业账户提示资质不够，可跳转提交企业认证材料', async ({ page }) => {
  await login(page, PERSONAL_ONLY_PHONE);
  await page.goto(join(USER_URL, '/mine/enterprise/procurement'));

  const gate = page.getByTestId('enterprise-gate');
  await expect(gate).toBeVisible({ timeout: 20_000 });
  await expect(gate).toContainText('企业资质');
  await expect(gate).toContainText('企业采购');

  const apply = page.getByTestId('enterprise-apply');
  await expect(apply).toBeVisible();
  await apply.click();

  await expect(page).toHaveURL(/\/enterprise\/register(\?|$)/, { timeout: 15_000 });
  // 认证材料：营业执照上传 + 企业名称 + 统一社会信用代码
  await expect(page.getByTestId('enterprise-license-upload')).toBeVisible();
  await expect(page.getByTestId('enterprise-company')).toBeVisible();
  await expect(page.getByTestId('enterprise-credit-code')).toBeVisible();
});
