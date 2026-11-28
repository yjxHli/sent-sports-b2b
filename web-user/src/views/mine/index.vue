<template>
  <div class="mine-page">
    <!-- 顶部资料卡：左头像 + 昵称，右三张小卡（对齐参考图的「我的淘宝」资料区） -->
    <section class="profile-card">
      <div class="profile-main">
        <!-- 头像即「编辑个人信息」入口（2026-11-17 用户要求） -->
        <button
          class="profile-avatar profile-avatar--edit"
          data-testid="edit-profile-entry"
          title="编辑头像和昵称"
          @click="router.push('/profile/edit')"
        >
          <img v-if="profile?.avatarFileId" class="profile-avatar__img"
               :src="fileApi.url(profile.avatarFileId)" alt="我的头像" />
          <template v-else>{{ avatarText }}</template>
        </button>
        <div class="profile-info">
          <div class="profile-name">
            {{ profile?.nickname || '森特会员' }}
            <span class="profile-level">{{ levelText }}</span>
          </div>
          <div class="profile-sub">
            <span>客户编号 {{ profile?.customerNo || '--' }}</span>
            <span class="profile-dot">·</span>
            <span>已登录</span>
          </div>
        </div>
        <router-link to="/member" class="profile-more">会员权益 ›</router-link>
      </div>

      <div class="profile-side">
        <!-- 2026-11-27 用户要求：右侧两块改成「积分 + 优惠券」，成长值挪回会员中心看 -->
        <router-link to="/member" class="side-mini">
          <span class="side-mini__label">可用积分</span>
          <b class="side-mini__num">{{ profile?.pointsBalance ?? '--' }}</b>
        </router-link>
        <router-link to="/coupons" class="side-mini">
          <span class="side-mini__label">我的优惠券</span>
          <b class="side-mini__num">{{ couponCount }}<i class="side-mini__unit">张</i></b>
        </router-link>
        <router-link to="/coupon/center" class="side-coupon" target="_blank" rel="noopener">
          <span class="side-coupon__icon">🎫</span>
          <span>前往领券中心<b>会员专享折扣券</b></span>
          <em>去领取</em>
        </router-link>
      </div>
    </section>

    <!-- 一行四卡：我的订单 / 我的收藏 / 我的足迹 / 购物车（2026-11-27 用户要求，对齐淘宝「我的」首页：
         订单统计与三张小卡同行，卡片浅底、圆角统一、无边框） -->
    <div class="home-row">
      <section class="panel home-card orders-panel">
        <header class="panel-head">
          <h3>我的订单</h3>
        </header>
        <div class="order-stats">
          <!-- 2026-11-28（第二次）：按用户要求改成「数字在上、文字在下」，不再配图标
               —— 图标 + 角标那版看着花，用户要的是「一眼看到几笔」 -->
          <button
            v-for="s in orderStats"
            :key="s.key"
            class="order-stat"
            :class="{ 'order-stat--hot': s.count > 0 }"
            @click="goOrders(s.key)"
          >
            <b class="order-stat__num">{{ s.count }}</b>
            <span class="order-stat__label">{{ s.label }}</span>
          </button>
        </div>
        <router-link class="panel-more orders-more" to="/orders">查看全部订单 ›</router-link>
        <p class="orders-hint">{{ latestOrderText }}</p>
      </section>

      <section class="panel home-card mini-panel">
        <header class="panel-head">
          <h3>我的收藏</h3>
          <router-link class="panel-more" to="/favorites">
            {{ favCount > 0 ? `全部 ${favCount} 件 ›` : '去逛逛 ›' }}
          </router-link>
        </header>
        <div v-if="!favorites.length" class="mini-empty">
          <div class="mini-empty__icon">☆</div>
          <p>还没有收藏宝贝</p>
          <router-link to="/products" class="mini-empty__btn">去逛逛</router-link>
        </div>
        <!-- 2026-11-28（第二次）：三卡统一口径 —— 商品图片 / 商品名称 / 商品价格，纵向排列 -->
        <button
          v-else
          type="button"
          class="mini-item"
          data-testid="mini-favorite"
          @click="goProduct(favorites[0].productId)"
        >
          <span class="mini-item__cover">
            <ProductThumb
              :src="favorites[0].mainImage"
              :name="favorites[0].productName"
              :seed="favorites[0].productId"
            />
          </span>
          <span class="mini-item__name">{{ favorites[0].productName }}</span>
          <b class="mini-item__price">¥{{ favorites[0].priceYuan || fmtCent(Number(favorites[0].priceCent ?? 0)) }}</b>
        </button>
      </section>

      <section class="panel home-card mini-panel">
        <header class="panel-head">
          <h3>我的足迹</h3>
          <router-link class="panel-more" to="/footprints">
            {{ footprintCount > 0 ? `全部 ${footprintCount} 件 ›` : '更多 ›' }}
          </router-link>
        </header>
        <div v-if="!footprints.length" class="mini-empty">
          <div class="mini-empty__icon">👣</div>
          <p>最近还没有浏览记录</p>
          <router-link to="/products" class="mini-empty__btn">去逛逛</router-link>
        </div>
        <button
          v-else
          type="button"
          class="mini-item"
          data-testid="mini-footprint"
          @click="goProduct(footprints[0].productId)"
        >
          <span class="mini-item__cover">
            <ProductThumb
              :src="footprints[0].mainImage"
              :name="footprints[0].productName"
              :seed="footprints[0].productId"
            />
          </span>
          <span class="mini-item__name">{{ footprints[0].productName }}</span>
          <b class="mini-item__price">¥{{ footprints[0].priceYuan || fmtCent(Number(footprints[0].priceCent ?? 0)) }}</b>
        </button>
      </section>

      <section class="panel home-card mini-panel cart-panel">
        <header class="panel-head">
          <h3>购物车</h3>
          <router-link class="panel-more" to="/cart">
            {{ cartCount > 0 ? `共 ${cartCount} 件 ›` : '去逛逛 ›' }}
          </router-link>
        </header>
        <div v-if="!cartFirst" class="mini-empty">
          <div class="mini-empty__icon">🛒</div>
          <p>购物车还是空的</p>
          <router-link to="/products" class="mini-empty__btn">去逛逛</router-link>
        </div>
        <button
          v-else
          type="button"
          class="mini-item"
          data-testid="mini-cart"
          @click="router.push('/cart')"
        >
          <span class="mini-item__cover">
            <ProductThumb
              :src="cartFirst.mainImage"
              :name="cartFirst.skuName"
              :seed="cartFirst.skuId"
            />
          </span>
          <span class="mini-item__name">{{ cartFirst.skuName || `商品规格 #${cartFirst.skuId}` }}</span>
          <b class="mini-item__price">¥{{ cartPriceText }}</b>
        </button>
      </section>
    </div>

    <!-- 猜你喜欢 -->
    <section class="panel like-panel">
      <header class="panel-head">
        <h3>猜你喜欢</h3>
        <router-link class="panel-more" to="/products" target="_blank" rel="noopener">更多好物 ›</router-link>
      </header>
      <div v-if="likes.length === 0" class="like-empty">暂无推荐</div>
      <div v-else class="like-grid">
        <GoodsCard
          v-for="p in likes"
          :key="p.productId"
          :product="p"
          new-tab
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * 个人中心首页「我的森特」（对齐「我的淘宝」版式）：资料卡 + 订单五状态 + 收藏/足迹/购物车三联卡 + 猜你喜欢。
 *
 * 2026-11-27 起本页渲染在 `layout/mine.vue` 的右侧内容区（个人中心独立外壳），
 * 自己不再管版心宽度；左侧导航、「森特 | 我的森特」标题与搜索框由外壳提供。
 *
 * 数据全部走真接口：`GET /api/me` 取资料、`GET /api/orders` 拉本人订单后在前端按状态计数
 * （用户端订单接口不分页筛选状态，客户自己的订单量级小，一次拉全比五个请求更省）；
 * 收藏 / 足迹 / 售后角标各自走 `/favorites`、`/footprints`、`/refunds/count`——
 * 2026-11-16 之前这三处分别是写死空态、写死空态、`Boolean(o.refundStatus)` 计出的 0。
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { OrderStatus, statusLabel, fmtCent, type UserMeVO } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { profileApi } from '@/api/profile';
import { fileApi } from '@/api/file';
import { orderApi, type OrderListItem } from '@/api/order';
import { productApi, type CatalogProductVO } from '@/api/product';
import { favoriteApi, type FavoriteVO } from '@/api/favorite';
import { footprintApi, type FootprintVO } from '@/api/footprint';
import { refundApi } from '@/api/refund';
import { couponApi } from '@/api/coupon';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { useCartStore } from '@/store/modules/useCartStore';
import GoodsCard from '@/components/GoodsCard.vue';
import ProductThumb from '@/components/ProductThumb.vue';
import { openInNewTab } from '@/utils/link';

const router = useRouter();
const auth = useAuthStore();
const cart = useCartStore();
const userId = computed(() => auth.userId);

const profile = ref<UserMeVO | null>(null);
const orders = ref<OrderListItem[]>([]);
const likes = ref<CatalogProductVO[]>([]);
const favorites = ref<FavoriteVO[]>([]);
const footprints = ref<FootprintVO[]>([]);
/** 售后角标：走 /api/refunds/count，不再拿订单上的 refundStatus 做布尔计数（该字段几乎恒为 null） */
const refundTotal = ref(0);
const favCount = ref(0);
const footprintCount = ref(0);
/** 可用券张数（2026-11-27：资料卡右侧改成「积分 + 优惠券」）：只数 status=UNUSED 的 */
const couponCount = ref(0);

const cartCount = computed(() => cart.totalQty);
/** 购物车卡要显示「图片 + 名称 + 价格」，取最新加购的那一件（列表按 id 倒序，第一条即最新） */
const cartFirst = computed(() => cart.items[0] ?? null);
/** 价格快照由后端给（`unitPriceYuan`）；Long 会被序列化成字符串，先 Number() 归一 */
const cartPriceText = computed(() => {
  const item = cartFirst.value;
  if (!item) return '--';
  if (item.unitPriceYuan) return String(item.unitPriceYuan);
  const cent = Number(item.unitPriceCent ?? 0);
  return Number.isFinite(cent) && cent > 0 ? fmtCent(cent) : '--';
});
const avatarText = computed(() => (profile.value?.nickname ?? '森特').slice(0, 1));
const levelText = computed(() => (profile.value ? statusLabel(profile.value.levelCode) : '--'));

/** 五状态计数：口径对齐订单列表页的标签；「退款/售后」用真实退款单量 */
const orderStats = computed(() => {
  const count = (predicate: (o: OrderListItem) => boolean): number =>
    orders.value.filter(predicate).length;
  return [
    { key: 'WAIT_PAY', label: '待付款', count: count((o) => o.status === OrderStatus.WAIT_PAY) },
    { key: 'WAIT_SHIP', label: '待发货', count: count((o) => o.status === OrderStatus.WAIT_SHIP) },
    {
      key: 'WAIT_RECEIVE',
      label: '待收货',
      count: count((o) => o.status === OrderStatus.WAIT_RECEIVE
        || o.status === OrderStatus.PART_SHIPPED),
    },
    { key: 'COMPLETED', label: '待评价', count: count((o) => o.status === OrderStatus.COMPLETED) },
    { key: 'REFUND', label: '退款/售后', count: refundTotal.value },
  ];
});

/** 订单卡底部一行：有订单就报最近一笔，没有就沿用淘宝的占位文案 */
const latestOrderText = computed(() => {
  const o = orders.value[0];
  if (!o) return '当前暂无物流信息更新';
  const st = o.status ? statusLabel(o.status) : '进行中';
  return `最近一笔 ${o.orderNo} · ${st}`;
});

async function loadProfile(): Promise<void> {
  if (!userId.value) return;
  try {
    profile.value = await unwrap(profileApi.me());
  } catch {
    profile.value = null;
  }
}

async function loadOrders(): Promise<void> {
  if (!userId.value) return;
  try {
    const page = await unwrap(orderApi.list({ page: 1, size: 100 }));
    orders.value = page?.list ?? [];
  } catch {
    orders.value = [];
  }
}

async function loadLikes(): Promise<void> {
  try {
    const page = await unwrap(productApi.list({ page: 1, size: 10 }));
    likes.value = page?.list ?? [];
  } catch {
    likes.value = [];
  }
}

/** 收藏：小卡只露最新 1 件（2026-11-28 用户要求），全部在 /favorites 里看 */
async function loadFavorites(): Promise<void> {
  if (!userId.value) return;
  try {
    const page = await unwrap(favoriteApi.list({ page: 1, size: 1 }));
    favorites.value = page?.list ?? [];
    favCount.value = Number(page?.total ?? 0);
  } catch {
    favorites.value = [];
    favCount.value = 0;
  }
}

/** 足迹：同上，只要最新 1 件 */
async function loadFootprints(): Promise<void> {
  if (!userId.value) return;
  try {
    const page = await unwrap(footprintApi.list({ page: 1, size: 1 }));
    footprints.value = page?.list ?? [];
    footprintCount.value = Number(page?.total ?? 0);
  } catch {
    footprints.value = [];
    footprintCount.value = 0;
  }
}

/**
 * 可用券张数：复用 `/api/coupons/mine`（与「我的优惠券」页同源），只数 UNUSED。
 * 券量级很小（演示账号十几张），一次拉全比新增一个 count 接口更省事，也不必改后端。
 */
async function loadCouponCount(): Promise<void> {
  if (!userId.value) return;
  try {
    const list = await unwrap(couponApi.mine());
    couponCount.value = (list ?? []).filter((c) => c.status === 'UNUSED').length;
  } catch {
    couponCount.value = 0;
  }
}

/**
 * 售后角标：单独查 `/api/refunds/count`。
 * 之前用 `Boolean(o.refundStatus)` 在本地订单列表上计数，而订单接口基本不回 refundStatus，
 * 于是「明明有退款单、个人中心却显示 0」。
 */
async function loadRefundCount(): Promise<void> {
  if (!userId.value) return;
  try {
    const res = await unwrap(refundApi.count());
    refundTotal.value = Number(res?.total ?? 0);
  } catch {
    refundTotal.value = 0;
  }
}

function goOrders(status: string): void {
  // 退款/售后有自己的列表页（售后单不等于订单），别把用户丢进订单列表筛一个不存在的状态
  if (status === 'REFUND') {
    router.push('/refunds');
    return;
  }
  // 2026-11-28：待评价直接进评价中心（用户要求），不再去订单列表筛「已完成」
  if (status === 'COMPLETED') {
    router.push('/mine/reviews');
    return;
  }
  router.push({ path: '/orders', query: { status } });
}
function goProduct(productId: string | number): void {
  openInNewTab(router, { path: `/product/${productId}` });
}

onMounted(async () => {
  await Promise.all([
    loadProfile(),
    loadOrders(),
    loadLikes(),
    loadFavorites(),
    loadFootprints(),
    loadRefundCount(),
    loadCouponCount(),
  ]);
  if (auth.isLoggedIn) {
    cart.load().catch(() => { /* 购物车失败不影响个人中心 */ });
  }
});
</script>

<style scoped>
.mine-page {
  /* 2026-11-27：个人中心改为独立外壳（layout/mine.vue），内容区宽度由外壳决定，这里不再自建版心 */
  width: 100%;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* ---------- 资料卡 ---------- */
.profile-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  background: linear-gradient(120deg, #127054 0%, #178a68 55%, #2aa87c 100%);
  border-radius: 8px;
  padding: 20px 22px;
  color: #fff;
}
.profile-main {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-right: 12px;
  min-width: 0;
}
.profile-avatar {
  width: 58px;
  height: 58px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.24);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  font-weight: 700;
  flex: none;
}
.profile-avatar--edit { border: none; cursor: pointer; color: inherit; padding: 0; overflow: hidden; }
.profile-avatar__img { width: 100%; height: 100%; object-fit: cover; }
.profile-avatar--edit:hover { box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.6); }
.profile-name {
  font-size: 18px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
}
.profile-level {
  font-size: 11px;
  font-weight: 500;
  background: rgba(255, 255, 255, 0.22);
  border-radius: 10px;
  padding: 1px 8px;
}
.profile-sub {
  margin-top: 6px;
  font-size: 12px;
  opacity: 0.82;
}
.profile-dot {
  margin: 0 6px;
}
.profile-more {
  /* 贴在资料区右侧、而不是被 1fr 顶到右侧区块边缘，避免看起来像悬浮按钮 */
  margin-left: 16px;
  flex: none;
  font-size: 13px;
  color: #fff;
  opacity: 0.9;
  border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 14px;
  padding: 4px 12px;
}
.profile-more:hover {
  background: rgba(255, 255, 255, 0.16);
}

.profile-side {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  align-content: start;
}
.side-mini {
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: rgba(255, 255, 255, 0.14);
  border-radius: 8px;
  padding: 8px 10px;
  color: #fff;
  transition: background 0.15s;
}
.side-mini:hover {
  background: rgba(255, 255, 255, 0.24);
}
.side-mini__label {
  font-size: 11px;
  opacity: 0.85;
}
.side-mini__num {
  font-size: 17px;
}
.side-mini__unit {
  margin-left: 2px;
  font-style: normal;
  font-size: 11px;
  font-weight: 400;
  opacity: 0.85;
}
.side-coupon {
  grid-column: span 2;
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  color: var(--sent-text);
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 12px;
}
.side-coupon__icon {
  font-size: 18px;
}
.side-coupon b {
  display: block;
  color: var(--sent-accent-dark);
  font-size: 12px;
}
.side-coupon em {
  margin-left: auto;
  font-style: normal;
  font-size: 11px;
  color: #fff;
  background: var(--sent-accent);
  border-radius: 9px;
  padding: 2px 8px;
}

/* ---------- 通用面板 ---------- */
.panel {
  /* 2026-11-27：个人中心统一白底（外壳也是白底），不再用卡片阴影分层，靠留白与字号区分 */
  background: #fff;
  border-radius: 8px;
  padding: 16px 18px;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.panel-head h3 {
  margin: 0;
  font-size: 16px;
  color: var(--sent-text);
}
.panel-more {
  font-size: 12px;
  color: var(--sent-muted);
}
.panel-more:hover {
  color: var(--sent-accent);
}

/* ---------- 订单五状态（2026-11-28 第二次：数字在上、文字在下，不配图标） ---------- */
.order-stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
  /* 2026-11-28 用户要求：五组数字与文字整体下移，和标题拉开距离 */
  padding-top: 18px;
}
.orders-more {
  display: block;
  margin-top: 12px;
  text-align: center;
}
.order-stat {
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 10px 4px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  transition: background 0.15s;
}
.order-stat:hover {
  background: var(--sent-primary-soft);
}
/* 0 笔也照常显示（用户要能看出「这一栏是数量」），有单才标红 */
.order-stat__num {
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 11px;
  background: #eef0f2;
  color: var(--sent-muted);
  font-size: 13px;
  font-weight: 700;
  line-height: 22px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.order-stat--hot .order-stat__num {
  background: #fdecee;
  color: #e34d59;
}
.order-stat__label {
  font-size: 12px;
  color: var(--sent-muted);
}

/* ---------- 一行四卡（订单 + 收藏 / 足迹 / 购物车） ---------- */
/* 2026-11-27：订单统计与三张小卡并排（对齐淘宝「我的」首页），订单卡约占 2.3 份宽 */
.home-row {
  display: grid;
  grid-template-columns: minmax(0, 2.3fr) repeat(3, minmax(0, 1fr));
  gap: 12px;
  align-items: stretch;
}
.home-card {
  display: flex;
  flex-direction: column;
  background: #fafbfc;
  border-radius: 8px;
  padding: 14px 16px 16px;
}
.home-card .panel-head {
  margin-bottom: 10px;
}
.home-card .panel-head h3 {
  font-size: 15px;
  font-weight: 600;
}
.orders-hint {
  margin: auto 0 0;
  padding-top: 10px;
  font-size: 12px;
  color: var(--sent-muted);
  text-align: center;
}
.mini-panel {
  /* 2026-11-28 第二次：三卡统一成「大图 + 名称 + 价格」，比原来左图右文高，卡片适当加高 */
  min-height: 258px;
}
.mini-empty {
  margin: auto 0;
  text-align: center;
  color: var(--sent-muted);
  font-size: 12px;
  padding: 8px 0 4px;
}
.mini-empty__icon {
  font-size: 28px;
  opacity: 0.3;
  margin-bottom: 6px;
}
.mini-empty__btn {
  display: inline-block;
  margin-top: 8px;
  border: 1px solid var(--sent-border);
  border-radius: 6px;
  padding: 4px 14px;
  font-size: 12px;
  color: var(--sent-text-2);
  background: #fff;
}
.mini-empty__btn:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}

/* 卡内商品（收藏 / 足迹 / 购物车同一套）：上大图、下名称、再价格，整体居中 */
.mini-item {
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  text-align: center;
  min-width: 0;
  width: 100%;
}
.mini-item__cover {
  display: block;
  width: 100%;
  height: 132px;
  border-radius: 8px;
  overflow: hidden;
  background: #f7f8f9;
}
/* 占位图组件自带 1:1，这里覆写成固定高度的横图区（选择器带父级，压得住组件自身的 .thumb） */
.mini-item__cover :deep(.thumb) {
  width: 100%;
  height: 100%;
  aspect-ratio: auto;
}
.mini-item__cover :deep(.thumb__emoji) {
  font-size: 34px;
}
.mini-item__name {
  font-size: 13px;
  line-height: 1.5;
  color: var(--sent-text-2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  width: 100%;
  min-height: 39px;
}
.mini-item:hover .mini-item__name {
  color: var(--sent-primary);
}
.mini-item__price {
  font-size: 15px;
  font-weight: 700;
  color: var(--sent-price);
}

/* 窄屏：订单卡独占一行，三张小卡退成一行三列；再窄就一列 */
@media (max-width: 1180px) {
  .home-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .orders-panel {
    grid-column: 1 / -1;
  }
}
@media (max-width: 720px) {
  .home-row {
    grid-template-columns: minmax(0, 1fr);
  }
}

/* ---------- 猜你喜欢 ---------- */
.like-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}
.like-empty {
  padding: 30px 0;
  text-align: center;
  color: var(--sent-muted);
  font-size: 13px;
}

@media (max-width: 1080px) {
  .profile-card {
    grid-template-columns: minmax(0, 1fr);
  }
  .mine-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .like-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
