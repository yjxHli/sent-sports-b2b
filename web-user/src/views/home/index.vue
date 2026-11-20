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
      <!-- 左侧分类导航 -->
      <aside class="cat-nav">
        <div class="cat-nav__title">全部商品分类</div>
        <ul class="cat-nav__list">
          <li
            v-for="c in categories"
            :key="c.categoryId"
            class="cat-nav__item"
            @click="goCategory(c.categoryId)"
          >
            <span>{{ c.name }}</span>
            <span class="cat-nav__arrow">›</span>
          </li>
          <li v-if="categories.length === 0" class="cat-nav__empty">暂无分类</li>
        </ul>
        <div class="cat-nav__all" @click="goProducts">查看全部商品 ›</div>
      </aside>

      <!-- 中间：活动瓦片 + 轮播 -->
      <div class="home-main">
        <div class="promo-grid">
          <div class="promo-main" @click="goCouponCenter">
            <div class="promo-main__badge">会员日</div>
            <div class="promo-main__title">领券中心</div>
            <p class="promo-main__sub">5 元无门槛 / 满 20 减 10 / 满 80 减 50</p>
            <p v-if="!isMemberDay" class="promo-main__next" data-testid="member-day-next">
              下次会员日：{{ nextMemberDayText }}
            </p>
            <div class="promo-main__cupons">
              <span>¥5</span><span>¥10</span><span>¥50</span>
            </div>
            <button class="promo-main__btn" @click.stop="goCouponCenter">立即领取</button>
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
              <div class="promo-tile__price">{{ t.price }}</div>
              <div class="promo-tile__emoji">{{ t.emoji }}</div>
            </div>
          </div>
        </div>

        <el-carousel height="200px" :interval="4500" arrow="hover" class="home-carousel">
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

      <!-- 右侧用户卡（对齐参考图的登录态信息面板） -->
      <aside class="side-card">
        <div class="side-user">
          <div class="side-user__avatar">{{ (profile?.nickname || '森').slice(0, 1) }}</div>
          <div class="side-user__info">
            <b>{{ profile?.nickname || (auth.isLoggedIn ? '森特会员' : '未登录') }}</b>
            <span>{{ profile ? statusLabel(profile.levelCode) : '登录后查看权益' }}</span>
          </div>
        </div>

        <div class="side-stats">
          <div class="side-stat">
            <b>{{ profile?.pointsBalance ?? 0 }}</b><span>积分</span>
          </div>
          <div class="side-stat">
            <b>{{ profile?.growthValue ?? 0 }}</b><span>成长值</span>
          </div>
          <div class="side-stat">
            <b>{{ cartCount }}</b><span>购物车</span>
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
          <b>企业采购？</b>
          <p>支持增值税专用发票、账期结算与批量阶梯价。</p>
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
  price: string;
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

const PROMO_TILES: PromoTile[] = [
  { key: 't1', title: '定制队服', tag: '来图即做', price: '¥151 起', emoji: '👕', bg: 'linear-gradient(135deg,#fff4ec,#ffe2cd)', path: '/custom' },
  { key: 't2', title: '跑步装备', tag: '新款直降', price: '¥182 起', emoji: '👟', bg: 'linear-gradient(135deg,#eef6ff,#d9e9ff)', query: { keyword: '跑步' } },
  { key: 't3', title: '健身器材', tag: '满减叠加', price: '¥329 起', emoji: '🏋️', bg: 'linear-gradient(135deg,#f2fbf4,#d8f0de)', query: { keyword: '训练' } },
  { key: 't4', title: '球类专场', tag: '比赛级', price: '¥89 起', emoji: '🏀', bg: 'linear-gradient(135deg,#fdf3f5,#fbdde3)', query: { categoryId: '1' } },
];

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
.home-hero {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr) 236px;
  gap: 14px;
  /* 左右两栏跟着中间轮播栏一起拉满高度，避免框底下漏出灰色空隙 */
  align-items: stretch;
}

/* 分类导航 */
.cat-nav {
  background: #fff;
  border-radius: var(--sent-radius);
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  padding-bottom: 6px;
  display: flex;
  flex-direction: column;
}
.cat-nav__title {
  background: var(--sent-primary);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  padding: 13px 18px;
  letter-spacing: 1px;
}
.cat-nav__list {
  list-style: none;
  margin: 0;
  padding: 6px 0;
  /* 分类项均分剩余高度，把卡片填满（只有 3~5 个分类时也不会留一大块白） */
  flex: 1;
  display: flex;
  flex-direction: column;
}
.cat-nav__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 11px 18px;
  min-height: 44px;
  flex: 1;
  font-size: 14px;
  color: var(--sent-text-2);
  cursor: pointer;
  transition: all 0.15s;
}
.cat-nav__item:hover {
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
  padding-left: 22px;
}
.cat-nav__arrow {
  color: #c6cbd3;
}
.cat-nav__item:hover .cat-nav__arrow {
  color: var(--sent-primary);
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
  margin: 6px 14px 0;
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

/* ---------- 活动瓦片 ---------- */
.promo-grid {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 10px;
}
.promo-main {
  background: linear-gradient(135deg, #ff5a2b 0%, #ff8a3d 100%);
  border-radius: 10px;
  color: #fff;
  padding: 14px 16px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}
.promo-main__badge {
  display: inline-block;
  font-size: 11px;
  background: rgba(255, 255, 255, 0.24);
  border-radius: 9px;
  padding: 2px 8px;
  margin-bottom: 8px;
}
.promo-main__title {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: 1px;
}
.promo-main__sub {
  margin: 6px 0 10px;
  font-size: 12px;
  opacity: 0.92;
}
.promo-main__next {
  margin: -4px 0 8px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: rgba(0, 0, 0, 0.18);
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
  background: rgba(255, 255, 255, 0.92);
  color: #ff4a1f;
  font-size: 12px;
  font-weight: 700;
  border-radius: 4px;
  padding: 3px 8px;
}
.promo-main__btn {
  border: none;
  background: #fff;
  color: #ff4a1f;
  font-size: 12px;
  font-weight: 700;
  border-radius: 14px;
  padding: 6px 16px;
  cursor: pointer;
}
.promo-tiles {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
.promo-tile {
  border-radius: 10px;
  padding: 10px 12px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  min-height: 108px;
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
}
.promo-tile__head b {
  font-size: 14px;
  color: var(--sent-text);
}
.promo-tile__head span {
  font-size: 11px;
  color: var(--sent-muted);
}
.promo-tile__price {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 700;
  color: var(--sent-accent-dark);
}
.promo-tile__emoji {
  position: absolute;
  right: 6px;
  bottom: 4px;
  font-size: 34px;
  opacity: 0.85;
}

/* ---------- 轮播 ---------- */
.home-carousel {
  border-radius: var(--sent-radius);
  overflow: hidden;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
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
  max-width: 470px;
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
  font-size: 26px;
  font-weight: 800;
  margin: 0 0 8px;
  letter-spacing: 1px;
}
.banner__sub {
  font-size: 13px;
  opacity: 0.9;
  margin: 0 0 16px;
  line-height: 1.6;
}
.banner__btn {
  border: none;
  cursor: pointer;
  background: #fff;
  color: var(--sent-primary);
  font-size: 13px;
  font-weight: 700;
  padding: 9px 26px;
  border-radius: 22px;
}
.banner__btn:hover {
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.18);
}
.banner__emoji {
  font-size: 96px;
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
/* 整宽后 4 列卡片过大，按 5 列排布更接近电商首页的密度 */
.home-section :deep(.goods-grid) {
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

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
  /* 2026-11-18：原 space-between 把富余高度均分到各块之间，看起来很散；
     改为内容自然紧凑靠上，新增收藏/足迹/客服入口把信息填满同一只卡片。 */
  gap: 9px;
}
.side-user {
  display: flex;
  align-items: center;
  gap: 10px;
}
.side-user__avatar {
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
  font-weight: 700;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.side-user__info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.side-user__info b {
  font-size: 14px;
  color: var(--sent-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.side-user__info span {
  font-size: 12px;
  color: var(--sent-accent-dark);
}
.side-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  background: #fafbfc;
  border-radius: 8px;
  padding: 8px 0;
}
.side-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.side-stat b {
  font-size: 15px;
  color: var(--sent-text);
}
.side-stat span {
  font-size: 11px;
  color: var(--sent-muted);
}
.side-links {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
}
.side-link {
  font-size: 12px;
  color: var(--sent-text-2);
  background: #fafbfc;
  border-radius: 6px;
  padding: 6px 4px;
  text-align: center;
  border: 1px solid #f2f3f5;
}
.side-link:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}
.side-link--chat {
  grid-column: span 2;
  background: var(--sent-accent-soft);
  border-color: #ffdcc2;
  color: var(--sent-accent-dark);
}
.side-tip {
  border-top: 1px dashed #eef0f2;
  padding-top: 8px;
  font-size: 12px;
  color: var(--sent-muted);
}
.side-tip b {
  color: var(--sent-text);
  font-size: 12px;
}
.side-tip p {
  margin: 4px 0 8px;
  line-height: 1.5;
}
.side-tip__btn {
  display: inline-block;
  font-size: 12px;
  color: #fff;
  background: var(--sent-primary);
  border-radius: 14px;
  padding: 5px 14px;
}
.side-tip__btn:hover {
  background: var(--sent-primary-dark);
}

@media (max-width: 1240px) {
  .home-hero {
    grid-template-columns: 190px minmax(0, 1fr);
  }
  .side-card {
    display: none;
  }
  .promo-grid {
    grid-template-columns: 220px minmax(0, 1fr);
  }
  .promo-tiles {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
