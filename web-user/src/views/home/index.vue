<template>
  <div class="container home">
    <!-- 顶部大促条（对齐参考图顶部红色通栏：文案 + 倒计时 + 立即抢购） -->
    <!-- 会员日横幅：仅周三、周四显示，其他日期不打扰 -->
    <div class="promo-bar" v-if="isMemberDay">
      <div class="promo-bar__left">
        <span class="promo-bar__tag">会员日</span>
        <b class="promo-bar__title">森特体育 · 巅峰返场 全场装备低至 6.8 折</b>
        <span class="promo-bar__sub">定制球衣第二个规格半价 · 企业团购再享账期</span>
      </div>
      <div class="promo-bar__right">
        <span class="promo-bar__label">距结束</span>
        <span class="promo-bar__clock">{{ countdown }}</span>
        <button class="promo-bar__btn" @click="goProducts">立即抢购</button>
      </div>
    </div>

    <div class="home-hero">
      <!-- 左侧分类导航：图标 + 同一行用「/」串起 2 个分类（参考图样式）。
           铺不下的分类不打紧，点「查看全部商品」在分类结果页的分类条里都能看到。 -->
      <aside class="cat-nav">
        <div class="cat-nav__title">分类</div>
        <ul class="cat-nav__list">
          <li v-for="row in catRows" :key="row.key" class="cat-nav__row">
            <span class="cat-nav__icon" aria-hidden="true">{{ row.icon }}</span>
            <span class="cat-nav__names">
              <template v-for="(c, i) in row.items" :key="c.categoryId">
                <span v-if="i" class="cat-nav__sep">/</span>
                <span class="cat-nav__item" @click="goCategory(c.categoryId)">{{ c.name }}</span>
              </template>
            </span>
          </li>
          <li v-if="!catRows.length" class="cat-nav__empty">暂无分类</li>
        </ul>
        <div class="cat-nav__all" @click="goProducts">查看全部商品 ›</div>
      </aside>

      <!-- 中间：券卡（200 权益侧栏）+ 主 banner（730）并排，让 banner 成为唯一主视觉；
           下方四个渠道瓦片改为 76px 横条，标题/起价一行读完，不再折行挤压。 -->
      <div class="home-main">
        <div class="promo-row">
          <div class="promo-main" @click="goCouponCenter">
            <div class="promo-main__badge">会员日</div>
            <div class="promo-main__title">领券中心</div>
            <p class="promo-main__sub">5元无门槛 · 满20减10 · 满80减50</p>
            <p v-if="!isMemberDay" class="promo-main__next" data-testid="member-day-next">
              下次会员日：{{ nextMemberDayText }}
            </p>
            <div class="promo-main__cupons">
              <span>¥5</span><span>¥10</span><span>¥50</span>
            </div>
            <button class="promo-main__btn" @click.stop="goCouponCenter">立即领取</button>
          </div>

          <el-carousel height="290px" :interval="4500" arrow="hover" class="home-carousel">
            <el-carousel-item v-for="b in banners" :key="b.key">
              <div class="banner" :style="{ background: b.bg }">
                <div class="banner__inner">
                  <div class="banner__tag">{{ b.tag }}</div>
                  <h2 class="banner__title">{{ b.title }}</h2>
                  <p class="banner__sub">{{ b.sub }}</p>
                  <button class="banner__btn" @click.stop="goEntry(b)">立即选购</button>
                </div>
                <div class="banner__emoji">{{ b.emoji }}</div>
              </div>
            </el-carousel-item>
          </el-carousel>
        </div>

        <div class="promo-tiles">
          <div
            v-for="t in PROMO_TILES"
            :key="t.key"
            class="promo-tile"
            :style="{ background: t.bg }"
            @click="goEntry(t)"
          >
            <div class="promo-tile__head">
              <b>{{ t.title }}</b>
              <span>{{ t.tag }}</span>
            </div>
            <div class="promo-tile__price">{{ tilePriceText(t) }}</div>
            <div class="promo-tile__emoji">{{ t.emoji }}</div>
          </div>
        </div>
      </div>

      <!-- 右侧用户卡（对齐参考图的登录态信息面板） -->
      <aside class="side-card">
        <div class="side-user">
          <div class="side-user__avatar" :class="{ 'side-user__avatar--guest': !profile }">
            {{ avatarChar }}
            <span v-if="profile" class="side-user__level">{{ statusLabel(profile.levelCode) }}</span>
          </div>
          <div class="side-user__info">
            <b>{{ profile?.nickname || (auth.isLoggedIn ? '森特会员' : '未登录') }}</b>
            <router-link v-if="!profile" to="/login" class="side-user__login">登录 / 注册</router-link>
          </div>
        </div>

        <div class="side-stats">
          <div class="side-stat" :class="{ 'side-stat--zero': isZeroStat(profile?.pointsBalance) }">
            <b>{{ statText(profile?.pointsBalance) }}</b><span>积分</span>
          </div>
          <div class="side-stat" :class="{ 'side-stat--zero': isZeroStat(profile?.growthValue) }">
            <b>{{ statText(profile?.growthValue) }}</b><span>成长值</span>
          </div>
          <div class="side-stat" :class="{ 'side-stat--zero': isZeroStat(cartCount) }">
            <b>{{ statText(cartCount) }}</b><span>购物车</span>
          </div>
        </div>

        <div class="side-links">
          <router-link to="/orders" class="side-link">📦 我的订单</router-link>
          <router-link to="/custom" class="side-link">🎨 我的定制</router-link>
          <router-link to="/favorites" class="side-link">⭐ 我的收藏</router-link>
          <router-link to="/footprints" class="side-link">👣 我的足迹</router-link>
          <router-link to="/chat" class="side-link side-link--chat">💬 在线客服</router-link>
        </div>

        <div class="side-tip">
          <b>企业采购</b>
          <p>专票·账期·阶梯价</p>
          <router-link :to="GROUP_BUY" class="side-tip__btn">查看团购专区</router-link>
        </div>
      </aside>
    </div>

    <!-- 猜你喜欢：整宽铺开（放在三栏之外，否则会被中栏挤成窄条） -->
    <section class="home-section">
      <div class="section-head">
        <h3>猜你喜欢</h3>
        <a class="section-more" @click="goProducts">查看全部 ›</a>
      </div>

      <div v-if="loading" class="list-hint">加载中…</div>
      <el-empty v-else-if="products.length === 0" description="暂无商品" />
      <div v-else class="goods-grid">
        <GoodsCard
          v-for="p in products"
          :key="p.productId"
          :product="p"
          @click="goDetail(p.productId)"
        />
      </div>
      <!-- 无限滚动哨兵：进入视口即加载下一页（放在条件分支之外，避免重渲染时被销毁） -->
      <div ref="sentinel" class="scroll-sentinel" aria-hidden="true"></div>
      <div v-if="loadingMore" class="list-hint">正在加载更多…</div>
      <div v-else-if="finished && products.length > 0" class="list-end">— 没有更多了 —</div>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * 商城首页（对齐参考图版式）：顶部大促通栏 + 左分类 + 中活动瓦片/轮播 + 右用户卡 + 猜你喜欢。
 * 数据仍全部来自真接口：分类、商品列表、`GET /api/me` 的会员资料。
 */
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { statusLabel, isMemberDay as checkMemberDay, nextMemberDayAt, type UserMeVO } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { productApi, type CategoryVO, type CatalogProductVO } from '@/api/product';
import { profileApi } from '@/api/profile';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { useCartStore } from '@/store/modules/useCartStore';
import GoodsCard from '@/components/GoodsCard.vue';

const router = useRouter();
const auth = useAuthStore();
const cart = useCartStore();

const categories = ref<CategoryVO[]>([]);
const products = ref<CatalogProductVO[]>([]);
const profile = ref<UserMeVO | null>(null);

/* ------------------------------------------------------------------
 * 左侧分类面板：参考图样式（图标 + 一行 2 个分类用 / 分隔）
 * ------------------------------------------------------------------ */
/** 一行放两个分类：球类/足球、篮球/羽毛球这种同族搭配读起来最顺 */
const CAT_PER_ROW = 2;
/** 左栏最多铺 8 行（16 个分类），再多的分类到「全部商品」页的分类条里看 */
const CAT_MAX_ROWS = 8;
/** 图标按名称关键词匹配，认不出的用默认奖牌图标兜底（新增分类不会变成空白格） */
const CAT_ICON_RULES: Array<[RegExp, string]> = [
  [/足球/, '⚽'], [/篮球/, '🏀'], [/羽毛球/, '🏸'], [/排球/, '🏐'], [/网球/, '🎾'],
  [/健身|器械|哑铃/, '🏋'], [/瑜伽/, '🧘'], [/跑步|跑鞋/, '🏃'],
  [/户外|登山|骑行|露营/, '⛰'], [/游泳|泳/, '🏊'], [/服饰|衣|裤|鞋/, '👕'],
  [/配件|包|水壶|护具/, '🎒'],
];

function catIcon(name: string): string {
  for (const [pattern, icon] of CAT_ICON_RULES) {
    if (pattern.test(name)) return icon;
  }
  return '🏅';
}

const catRows = computed(() => {
  const visible = categories.value.slice(0, CAT_PER_ROW * CAT_MAX_ROWS);
  return Array.from({ length: Math.ceil(visible.length / CAT_PER_ROW) }, (_, i) => {
    const items = visible.slice(i * CAT_PER_ROW, (i + 1) * CAT_PER_ROW);
    return { key: items.map((c) => c.categoryId).join('-'), icon: catIcon(items[0].name), items };
  });
});
/** 首屏加载（占位骨架）/ 追页加载 / 已到底 */
const loading = ref(false);
const loadingMore = ref(false);
const finished = ref(false);
/** 当前已加载到第几页 + 后端总数（`JacksonConfig` 会把 Long 序列化成字符串，必须 Number） */
const page = ref(1);
const total = ref(0);
const sentinel = ref<HTMLElement | null>(null);
let observer: IntersectionObserver | undefined;

const cartCount = computed(() => cart.totalQty);

/* ---------------- 右侧用户卡展示辅助 ---------------- */
/**
 * 头像首字：取昵称首字。
 * 注意：演示数据里的昵称是客户/企业名（如「验收客户」），不是真实人名，
 * 「姓氏首字」在数据层面拿不到 —— 这里只能用首字，靠等级徽标 + 中性配色
 * 消除「像验证通过标识」的歧义。真实修复需要后端 profile 提供真实姓名/头像。
 */
const avatarChar = computed(() => (profile.value?.nickname || '森').slice(0, 1));

/** 数字展示：千分位；无数据（未登录 / 接口失败）显示「—」而不是 0 */
function statText(v: number | string | null | undefined): string {
  if (v === null || v === undefined || v === '') return '—';
  const n = Number(v);
  return Number.isFinite(n) ? n.toLocaleString('zh-CN') : '—';
}

/** 0 值降灰的判据：无数据也按 0 处理（未登录时不该显示成有数据） */
function isZeroStat(v: number | string | null | undefined): boolean {
  if (v === null || v === undefined || v === '') return true;
  return Number(v) === 0;
}
/** 周三(3)/周四(4) 是会员日：顶部大促横幅只在这两天出现 */
/** 会员日判断走 shared 纯函数（与后端 MemberDayPolicy 同口径），now 每秒刷新保证跨零点自动切换 */
const now = ref(new Date());
const isMemberDay = computed(() => checkMemberDay(now.value));
const nextMemberDayText = computed(() => {
  const d = nextMemberDayAt(now.value);
  const pad = (n: number): string => String(n).padStart(2, '0');
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()];
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日（周${week}）${pad(d.getHours())}:${pad(d.getMinutes())}`;
});

/**
 * 顶部各模块的落点配置。
 * 每个入口都要指向「自己对应的页面或商品列表」，不再统一跳全部商品；
 * `path` 与 `query` 二选一，`query` 一律落到 `/products` 的筛选条件上。
 */
interface EntryLink {
  path?: string;
  query?: Record<string, string>;
}

interface PromoTile extends EntryLink {
  key: string;
  title: string;
  tag: string;
  emoji: string;
  bg: string;
}

interface BannerItem extends EntryLink {
  key: string;
  tag: string;
  title: string;
  sub: string;
  bg: string;
  emoji: string;
}

/**
 * 四个促销瓦片。
 * **价格不再写死**（2026-11-21）：原先的 `¥151 / ¥182 / ¥329 / ¥89 起` 与接口实测的
 * 真实零售价区间 10~40 元差了 4~10 倍，属可信度问题。现在起价一律由
 * `loadTilePrices()` 从 `/api/products` 的真实 SKU 零售价里取最小值。
 */
const PROMO_TILES: PromoTile[] = [
  { key: 't1', title: '定制队服', tag: '来图即做', emoji: '👕', bg: 'linear-gradient(135deg,#fff4ec,#ffe2cd)', path: '/custom' },
  { key: 't2', title: '跑步装备', tag: '新款直降', emoji: '👟', bg: 'linear-gradient(135deg,#eef6ff,#d9e9ff)', query: { keyword: '跑步' } },
  { key: 't3', title: '健身器材', tag: '满减叠加', emoji: '🏋️', bg: 'linear-gradient(135deg,#f2fbf4,#d8f0de)', query: { keyword: '训练' } },
  { key: 't4', title: '球类专场', tag: '比赛级', emoji: '🏀', bg: 'linear-gradient(135deg,#fdf3f5,#fbdde3)', query: { categoryId: '1' } },
];

/** 瓦片 key → 该类目/关键词下的真实最低零售价（分）；null 表示查询失败或无在售 SKU */
const tilePriceCent = ref<Record<string, number | null>>({});

/** 单页取全的上限，与商品结果页排序时一致（PageQuery.MAX_SIZE） */
const TILE_PRICE_PAGE_SIZE = 100;

/** 从商品列表里取「在售 SKU 的最低零售价」（分） */
function minSkuCent(list: CatalogProductVO[]): number | null {
  let min: number | null = null;
  for (const p of list) {
    for (const s of p.skus ?? []) {
      if (s.onShelf === false) continue;
      const cent = Number(s.price?.retailPriceCent ?? 0);
      if (cent > 0 && (min === null || cent < min)) min = cent;
    }
  }
  return min;
}

/** 并发拉三个商品类瓦片的真实最低价（定制是服务入口，无商品报价可比，不参与） */
async function loadTilePrices(): Promise<void> {
  const jobs = PROMO_TILES.filter((t) => t.query).map(async (t) => {
    const q = t.query ?? {};
    try {
      const res = await unwrap(
        productApi.list({
          keyword: q.keyword,
          categoryId: q.categoryId ? Number(q.categoryId) : undefined,
          page: 1,
          size: TILE_PRICE_PAGE_SIZE,
        }),
      );
      tilePriceCent.value[t.key] = minSkuCent(res.list ?? []);
    } catch {
      tilePriceCent.value[t.key] = null;
    }
  });
  await Promise.allSettled(jobs);
}

/** 分 → 展示用的元（整数不带小数，如 1000 分 → "10"；非整数保留两位） */
function yuanCompact(cent: number): string {
  return cent % 100 === 0 ? String(cent / 100) : (cent / 100).toFixed(2);
}

/**
 * 瓦片价格文案。
 * - 商品类瓦片：显示真实起价；
 * - 定制瓦片：定制费按件结算、没有可比商品价 → 显示「按件计价」，**不编造数字**；
 * - 查询失败：留空，不退回写死值。
 */
function tilePriceText(t: PromoTile): string {
  const cent = tilePriceCent.value[t.key];
  if (cent && cent > 0) return `¥${yuanCompact(cent)} 起`;
  if (t.path) return '按件计价';
  return '';
}

/** 企业采购「团购专区」：独立的团购专区页（阶梯价 / 账期 / 专票），不再复用训练关键词 */
const GROUP_BUY = { path: '/group-buy' };

const banners: BannerItem[] = [
  {
    key: 'b1',
    tag: '企业团购季',
    title: '专业训练装备 · 一站配齐',
    sub: '球类 / 健身 / 瑜伽 / 户外，支持 UV 定制与账期结算',
    bg: 'linear-gradient(120deg, #12664f 0%, #1c8a6b 55%, #2fae86 100%)',
    emoji: '🏀',
    query: { keyword: '训练' },
  },
  {
    key: 'b2',
    tag: '新品上市',
    title: '缓震科技 · 跑出每一步',
    sub: '全新一代跑步装备，会员下单立享专属价',
    bg: 'linear-gradient(120deg, #ff7a1a 0%, #ff9a4d 60%, #ffb877 100%)',
    emoji: '👟',
    query: { keyword: '跑步' },
  },
  {
    key: 'b3',
    tag: '定制服务',
    title: '来图定制 · 打造专属队服',
    sub: 'UV 印花 / 烫印队徽，30 件起订，最快 7 天发货',
    bg: 'linear-gradient(120deg, #1f3a5f 0%, #2b5285 60%, #3f6fa8 100%)',
    emoji: '👕',
    path: '/custom',
  },
];

/** 促销倒计时（仅展示用，固定到当日 24:00 结束） */
const countdown = ref('00:00:00');
let clockTimer: ReturnType<typeof setInterval> | undefined;

function tick(): void {
  now.value = new Date();
  const nowDate = now.value;
  const end = new Date(nowDate);
  end.setHours(23, 59, 59, 999);
  const diff = Math.max(0, end.getTime() - nowDate.getTime());
  const h = Math.floor(diff / 3_600_000);
  const m = Math.floor((diff % 3_600_000) / 60_000);
  const s = Math.floor((diff % 60_000) / 1000);
  const pad = (n: number): string => String(n).padStart(2, '0');
  countdown.value = `${pad(h)}:${pad(m)}:${pad(s)}`;
}

function goProducts(): void {
  router.push('/products');
}
/** 领券中心 → 会员中心（券包与权益都在这里查看） */
/** 领券中心：进专门的领券页（上可领券 + 下商品），不再跳会员中心 */
function goCouponCenter(): void {
  router.push('/coupon/center');
}
/** 按入口配置跳转：有 path 直跳，否则带筛选条件进商品结果页 */
function goEntry(e: EntryLink): void {
  if (e.path) {
    router.push(e.path);
    return;
  }
  router.push({ path: '/products', query: e.query ?? {} });
}
/**
 * 点分类：在**新标签页**打开独立的分类结果页（对齐淘宝：首页不被导航走，另开一页看分类）。
 * 用 `router.resolve` 生成带 base 的完整 href，base 不是 `/` 时也不会拼错路径。
 */
function goCategory(id: string | number): void {
  const { href } = router.resolve({ path: '/products', query: { categoryId: String(id) } });
  window.open(href, '_blank');
}
function goDetail(id: string): void {
  router.push(`/product/${id}`);
}

/** 猜你喜欢一页条数（无限滚动每页追加量） */
const PAGE_SIZE = 10;
/** 哨兵提前量：进入底部 240px 内就开始预取下一页 */
const PRELOAD_PX = 240;

/** 按 productId 去重追加，避免翻页期间数据变动导致重复卡片 */
function mergeProducts(list: CatalogProductVO[]): void {
  const seen = new Set(products.value.map((p) => p.productId));
  for (const item of list) {
    if (seen.has(item.productId)) continue;
    seen.add(item.productId);
    products.value.push(item);
  }
}

async function fetchPage(target: number, append: boolean): Promise<void> {
  if (append) loadingMore.value = true;
  else loading.value = true;
  try {
    const res = await unwrap(productApi.list({ page: target, size: PAGE_SIZE }));
    const list = res.list ?? [];
    total.value = Number(res.total ?? 0);
    if (append) mergeProducts(list);
    else products.value = list.slice();
    page.value = target;
    if (list.length === 0 || (total.value > 0 && products.value.length >= total.value)) {
      finished.value = true;
    }
  } finally {
    if (append) loadingMore.value = false;
    else loading.value = false;
  }
}

/** 哨兵是否已进入预加载区（视口底部 240px 内） */
function sentinelNear(): boolean {
  const el = sentinel.value;
  if (!el) return false;
  return el.getBoundingClientRect().top <= window.innerHeight + PRELOAD_PX;
}

async function loadMore(): Promise<void> {
  if (loading.value || loadingMore.value || finished.value) return;
  if (total.value > 0 && products.value.length >= total.value) {
    finished.value = true;
    return;
  }
  try {
    await fetchPage(page.value + 1, true);
  } catch {
    finished.value = true;
    return;
  }
  await nextTick();
  // 新追加的一页仍没填满视口时继续补，否则用户还得再手工下拉一次
  if (sentinelNear()) void loadMore();
}

function setupObserver(): void {
  if (!sentinel.value || typeof IntersectionObserver === 'undefined') return;
  observer?.disconnect();
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) void loadMore();
    },
    { rootMargin: `${PRELOAD_PX}px 0px` },
  );
  observer.observe(sentinel.value);
}

/**
 * 滚动兜底：IntersectionObserver 只在「交叉状态变化」时回调，
 * 若哨兵一直停在预加载区内（页面短、切分类后列表变短）就再也不会触发。
 * 这里每帧最多检查一次，作为保底。
 */
let scrollRaf = 0;
function onWindowScroll(): void {
  if (scrollRaf) return;
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = 0;
    if (sentinelNear()) void loadMore();
  });
}

onMounted(async () => {
  tick();
  clockTimer = setInterval(tick, 1000);
  // 瓦片起价走真实商品数据，不阻塞首屏其它数据链路
  void loadTilePrices();

  try {
    categories.value = (await unwrap(productApi.categories())) ?? [];
  } catch {
    categories.value = [];
  }
  try {
    await fetchPage(1, false);
  } catch {
    products.value = [];
    finished.value = true;
  }
  await nextTick();
  setupObserver();
  window.addEventListener('scroll', onWindowScroll, { passive: true });
  if (auth.isLoggedIn) {
    try {
      profile.value = await unwrap(profileApi.me());
    } catch {
      profile.value = null;
    }
  }
});

onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer);
  window.removeEventListener('scroll', onWindowScroll);
  if (scrollRaf) cancelAnimationFrame(scrollRaf);
  observer?.disconnect();
  observer = undefined;
});
</script>

<style scoped>
.home {
  padding-top: 14px;
}

/* ---------- 顶部大促通栏 ---------- */
.promo-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  background: linear-gradient(90deg, #ff4a1f 0%, #ff7a1a 48%, #ff9d3d 100%);
  border-radius: 10px;
  padding: 12px 18px;
  color: #fff;
  margin-bottom: 14px;
}
.promo-bar__left {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}
.promo-bar__tag {
  font-size: 11px;
  background: rgba(255, 255, 255, 0.22);
  border-radius: 9px;
  padding: 2px 8px;
  flex: none;
}
.promo-bar__title {
  font-size: 17px;
  letter-spacing: 0.5px;
}
.promo-bar__sub {
  font-size: 12px;
  opacity: 0.9;
}
.promo-bar__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}
.promo-bar__label {
  font-size: 12px;
  opacity: 0.9;
}
.promo-bar__clock {
  font-size: 15px;
  font-weight: 700;
  background: rgba(0, 0, 0, 0.22);
  border-radius: 6px;
  padding: 3px 10px;
  font-variant-numeric: tabular-nums;
}
.promo-bar__btn {
  border: none;
  background: #fff;
  color: #ff4a1f;
  font-weight: 700;
  font-size: 13px;
  border-radius: 16px;
  padding: 7px 18px;
  cursor: pointer;
}
.promo-bar__btn:hover {
  box-shadow: 0 6px 14px rgba(0, 0, 0, 0.18);
}

/* ---------- 主区三栏 ---------- */
/* 版心 1440 下的分配：210 分类 + 942 中栏 + 260 用户卡。
   中栏 942 = 券卡 200 + gap 12 + banner 730，banner 比例 730:290 ≈ 2.5:1（接近淘宝的 2.44:1）。 */
.home-hero {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr) 260px;
  gap: 14px;
  /* 三栏等高，避免框底下漏出灰色空隙 */
  align-items: stretch;
}

/* 分类导航（参考图版式：图标 + 一行多个分类用 / 分隔） */
.cat-nav {
  background: #fff;
  border-radius: var(--sent-radius);
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  padding-bottom: 4px;
  display: flex;
  flex-direction: column;
}
.cat-nav__title {
  background: var(--sent-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  padding: 12px 18px;
  letter-spacing: 1px;
}
.cat-nav__list {
  list-style: none;
  margin: 0;
  padding: 8px 8px 4px;
  /* 行数少于可用高度时按比例撑满，左栏永远和右侧对齐，不留半块白 */
  flex: 1;
  display: flex;
  flex-direction: column;
}
.cat-nav__row {
  flex: 1;
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 0 8px;
  border-radius: 8px;
  transition: background 0.15s;
}
.cat-nav__row:hover {
  background: var(--sent-primary-soft);
}
.cat-nav__icon {
  width: 20px;
  flex: none;
  text-align: center;
  font-size: 14px;
  line-height: 1;
}
.cat-nav__names {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  min-width: 0;
}
.cat-nav__item {
  font-size: 13.5px;
  color: var(--sent-text-2);
  cursor: pointer;
  transition: color 0.15s;
}
.cat-nav__item:hover {
  color: var(--sent-primary);
  text-decoration: underline;
}
.cat-nav__sep {
  margin: 0 7px;
  color: #c9d2cd;
  font-size: 12px;
}
.cat-nav__empty {
  padding: 12px 18px;
  color: var(--sent-muted);
  font-size: 13px;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.cat-nav__all {
  margin: 4px 14px 0;
  padding: 9px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-primary);
  border-top: 1px dashed #eef0f2;
  cursor: pointer;
}
.cat-nav__all:hover {
  color: var(--sent-accent);
}

.home-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
/* ---------- 券卡 + banner 并排 ---------- */
.promo-row {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 12px;
}
.promo-main {
  /* 2026-11-21 双橙主视觉处理：券卡由「橙渐变大块」降级为「白底 + 橙描边」，
     首屏只留 banner 一个彩色主视觉，券卡退回权益侧栏的位置。 */
  background: #fff;
  border: 1px solid #ffd9bf;
  border-radius: 10px;
  color: var(--sent-text);
  padding: 14px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  /* 200×290 的竖长条：内容纵向铺开，按钮沉底 */
  display: flex;
  flex-direction: column;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.promo-main:hover {
  border-color: var(--sent-accent);
  box-shadow: 0 6px 16px rgba(255, 122, 26, 0.12);
}
.promo-main__badge {
  display: inline-block;
  align-self: flex-start;
  font-size: 11px;
  background: var(--sent-accent-soft);
  color: var(--sent-accent-dark);
  border-radius: 9px;
  padding: 2px 8px;
  margin-bottom: 8px;
}
.promo-main__title {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 1px;
  color: var(--sent-accent-dark);
}
.promo-main__sub {
  margin: 6px 0 10px;
  font-size: 12px;
  color: var(--sent-text-2);
  line-height: 1.6;
}
.promo-main__next {
  margin: 0 0 10px;
  font-size: 11px;
  font-weight: 600;
  color: var(--sent-accent-dark);
  background: var(--sent-accent-soft);
  border-radius: 4px;
  padding: 4px 6px;
  line-height: 1.4;
}
.promo-main__cupons {
  display: flex;
  gap: 6px;
  margin-bottom: 12px;
}
.promo-main__cupons span {
  background: #fff;
  border: 1px solid #ffd9bf;
  color: var(--sent-accent-dark);
  font-size: 12px;
  font-weight: 700;
  border-radius: 4px;
  padding: 3px 8px;
}
.promo-main__btn {
  border: none;
  background: var(--sent-accent);
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  border-radius: 14px;
  padding: 6px 16px;
  cursor: pointer;
  /* 券卡变竖长条后，按钮沉到底部 */
  margin-top: auto;
  align-self: flex-start;
}
.promo-main__btn:hover {
  background: var(--sent-accent-dark);
}
.promo-tiles {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
/* 横条瓦片（76px 高）：左侧标题/标签两行，右侧起价与图标同一行读完，不再折行 */
.promo-tile {
  border-radius: 10px;
  padding: 0 12px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  min-height: 76px;
  display: flex;
  align-items: center;
  gap: 8px;
  transition: transform 0.15s, box-shadow 0.15s;
}
.promo-tile:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.08);
}
.promo-tile__head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1 1 auto;
  min-width: 0;
}
.promo-tile__head b {
  font-size: 14px;
  color: var(--sent-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.promo-tile__head span {
  font-size: 11px;
  color: var(--sent-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.promo-tile__price {
  flex: none;
  font-size: 13px;
  font-weight: 700;
  color: var(--sent-accent-dark);
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.promo-tile__emoji {
  flex: none;
  font-size: 24px;
  line-height: 1;
  opacity: 0.55;
}

/* ---------- 轮播（banner） ---------- */
/* 高度由模板的 height="290px" 决定（730×290 ≈ 2.5:1）；这里只管圆角与裁切 */
.home-carousel {
  height: 290px;
  border-radius: var(--sent-radius);
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
}
.home-carousel :deep(.el-carousel__container) {
  height: 100% !important;
}
.banner {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 40px;
  color: #fff;
  position: relative;
}
.banner__inner {
  max-width: 560px;
}
.banner__tag {
  display: inline-block;
  padding: 3px 12px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.22);
  font-size: 12px;
  letter-spacing: 1px;
  margin-bottom: 10px;
}
.banner__title {
  font-size: 30px;
  font-weight: 800;
  margin: 0 0 10px;
  letter-spacing: 1px;
}
.banner__sub {
  font-size: 14px;
  opacity: 0.9;
  margin: 0 0 18px;
  line-height: 1.7;
}
.banner__btn {
  border: none;
  cursor: pointer;
  background: #fff;
  color: var(--sent-primary);
  font-size: 13px;
  font-weight: 700;
  padding: 10px 28px;
  border-radius: 22px;
}
.banner__btn:hover {
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.18);
}
.banner__emoji {
  font-size: 120px;
  line-height: 1;
  opacity: 0.9;
  filter: drop-shadow(0 10px 20px rgba(0, 0, 0, 0.22));
}

/* ---------- 猜你喜欢 ---------- */
.home-section {
  margin-top: 14px;
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 16px;
  /* 点分类后 scrollIntoView 落位时，给吸顶导航留出空间 */
  scroll-margin-top: 64px;
}
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}
.section-head h3 {
  font-size: 18px;
  margin: 0;
  color: var(--sent-text);
  position: relative;
  padding-left: 14px;
}
.section-head h3::before {
  content: '';
  position: absolute;
  left: 0;
  top: 3px;
  bottom: 3px;
  width: 4px;
  border-radius: 2px;
  background: var(--sent-accent);
}
.section-more {
  font-size: 13px;
  color: var(--sent-muted);
  cursor: pointer;
}
.section-more:hover {
  color: var(--sent-accent);
}
/* 网格列数交给全局 `.goods-grid`（auto-fill / minmax(216px, 1fr)）：
   1440 版心下自动排 6 列，窄屏自动降列，不再在这里写死 5 列。
   2026-11-21 设计决策：与分类页/收藏页等 6 处商品网格统一口径，避免一页 6 一页 5。 */

/* ---------- 无限滚动 ---------- */
.scroll-sentinel {
  height: 1px;
}
.list-end {
  padding: 18px 0 4px;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
  letter-spacing: 1px;
}
.list-hint {
  padding: 16px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}

/* ---------- 右侧用户卡 ---------- */
.side-card {
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 14px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  /* 2026-11-21 方案 C：删掉 space-between —— 它把富余高度摊成几条空白带。
     改为紧凑排布，剩余高度交给底部「企业采购」块（margin-top:auto + 浅绿底）吸收。 */
  justify-content: flex-start;
  gap: 12px;
}
.side-user {
  display: flex;
  align-items: center;
  gap: 10px;
}
/* 头像：改中性暖色底（原品牌绿底 + 白字「验」最像「验证通过」标识），右下角挂等级徽标 */
.side-user__avatar {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #fff1e6, #ffe0c7);
  color: #b4531a;
  font-weight: 700;
  font-size: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.side-user__avatar--guest {
  background: #f1f2f4;
  color: var(--sent-muted);
}
/* 等级徽标：把「等级」从一行小字变成头像上的身份标记，同时避免与价格色抢戏 */
.side-user__level {
  position: absolute;
  right: -6px;
  bottom: -5px;
  font-size: 10px;
  line-height: 1;
  padding: 3px 5px;
  border-radius: 8px;
  background: var(--sent-primary);
  color: #fff;
  font-weight: 600;
  white-space: nowrap;
}
.side-user__info {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
}
.side-user__info b {
  font-size: 14px;
  color: var(--sent-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 会员数据缺失时的唯一出口：不能只留一句「未登录」 */
.side-user__login {
  align-self: flex-start;
  font-size: 12px;
  color: var(--sent-accent-dark);
  border-bottom: 1px solid currentColor;
  padding-bottom: 1px;
}
.side-user__login:hover {
  color: var(--sent-accent);
}
.side-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  background: #fafbfc;
  border-radius: 8px;
  padding: 9px 0;
}
.side-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
}
.side-stat b {
  font-size: 15px;
  color: var(--sent-text);
  /* 数字等宽，避免刷新/切页时抖动 */
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.2px;
}
/* 0 值降灰：把视觉重量让给真正有数的指标 */
.side-stat--zero b {
  color: var(--sent-muted);
  font-weight: 500;
}
.side-stat span {
  font-size: 11px;
  color: var(--sent-muted);
}
.side-links {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2px 6px;
}
/* 纯文字行：去掉灰底 + 灰边，消除「卡片套卡片」的视觉噪点 */
.side-link {
  font-size: 12.5px;
  color: var(--sent-text-2);
  padding: 7px 6px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 6px;
  transition: background 0.15s, color 0.15s;
}
.side-link:hover {
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
}
/* 在线客服：幽灵描边 —— 首屏只保留一个实心 CTA（团购） */
.side-link--chat {
  grid-column: span 2;
  justify-content: center;
  border: 1px solid #ffdcc2;
  color: var(--sent-accent-dark);
  font-weight: 600;
}
.side-link--chat:hover {
  background: var(--sent-accent-soft);
  border-color: var(--sent-accent);
  color: var(--sent-accent-dark);
}
.side-tip {
  /* 贴底吸收剩余高度：浅绿底块看起来是「一块有内容的面板」，而不是一条空白带 */
  margin-top: auto;
  background: var(--sent-primary-soft);
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 12px;
  color: var(--sent-text-2);
  /* 标题 / 说明在左，按钮在右且跨两行 —— 省掉一行高度 */
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-rows: auto auto;
  align-items: center;
  gap: 2px 8px;
}
.side-tip b {
  grid-column: 1;
  grid-row: 1;
  color: var(--sent-text);
  font-size: 12.5px;
}
.side-tip p {
  grid-column: 1;
  grid-row: 2;
  margin: 0;
  line-height: 1.5;
}
.side-tip__btn {
  grid-column: 2;
  grid-row: 1 / span 2;
  display: inline-block;
  font-size: 12px;
  color: #fff;
  background: var(--sent-primary);
  border-radius: 14px;
  padding: 6px 12px;
  white-space: nowrap;
}
.side-tip__btn:hover {
  background: var(--sent-primary-dark);
}

/* 1440 版心需要约 1440 的视口；不够时降为两栏，用户卡**下移成整宽横条**，
   不再 display:none（那会把订单/收藏/足迹/在线客服/团购六个入口一起砍掉）。 */
@media (max-width: 1380px) {
  .home-hero {
    grid-template-columns: 190px minmax(0, 1fr);
  }
  .side-card {
    grid-column: 1 / -1;
    flex-direction: row;
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 14px 24px;
  }
  .side-user {
    flex: none;
  }
  .side-stats {
    flex: 1 1 220px;
  }
  .side-links {
    flex: 1 1 240px;
    display: flex;
    flex-wrap: wrap;
    gap: 2px 4px;
  }
  .side-tip {
    flex: 1 1 100%;
    margin-top: 0;
  }
}
@media (max-width: 1240px) {
  .promo-row {
    grid-template-columns: 220px minmax(0, 1fr);
  }
  .promo-tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
/* 手机/窄屏：用户卡回到纵向排布 */
@media (max-width: 900px) {
  .side-card {
    flex-direction: column;
  }
  .side-links {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
  .side-link--chat {
    grid-column: span 2;
  }
}
</style>
