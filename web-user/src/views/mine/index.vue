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
        <div class="side-mini">
          <span class="side-mini__label">可用积分</span>
          <b class="side-mini__num">{{ profile?.pointsBalance ?? '--' }}</b>
        </div>
        <div class="side-mini">
          <span class="side-mini__label">成长值</span>
          <b class="side-mini__num">{{ profile?.growthValue ?? '--' }}</b>
        </div>
        <router-link to="/member" class="side-coupon">
          <span class="side-coupon__icon">🎫</span>
          <span>前往会员中心<b>会员专享折扣券</b></span>
          <em>去领取</em>
        </router-link>
      </div>
    </section>

    <!-- 我的订单：5 个状态计数 + 全部订单入口 -->
    <section class="panel orders-panel">
      <header class="panel-head">
        <h3>我的订单</h3>
        <router-link class="panel-more" to="/orders">查看全部订单 ›</router-link>
      </header>
      <div class="order-stats">
        <button
          v-for="s in orderStats"
          :key="s.key"
          class="order-stat"
          @click="goOrders(s.key)"
        >
          <b :class="{ 'order-stat__num--hot': s.count > 0 }">{{ s.count }}</b>
          <span>{{ s.label }}</span>
        </button>
      </div>
    </section>

    <!-- 下方三联卡：收藏 / 足迹 / 购物车 -->
    <div class="mine-grid">
      <section class="panel mini-panel">
        <header class="panel-head">
          <h3>我的收藏</h3>
          <router-link class="panel-more" to="/favorites">
            {{ favCount > 0 ? `全部 ${favCount} 件 ›` : '去逛逛 ›' }}
          </router-link>
        </header>
        <div v-if="favorites.length === 0" class="mini-empty">
          <div class="mini-empty__icon">☆</div>
          <p>还没有收藏商品</p>
        </div>
        <div v-else class="mini-strip">
          <button
            v-for="f in favorites"
            :key="f.productId"
            type="button"
            class="mini-item"
            @click="goProduct(f.productId)"
          >
            <ProductThumb class="mini-item__thumb" :src="f.mainImage" :name="f.productName" :seed="f.productId" />
            <span class="mini-item__name">{{ f.productName }}</span>
          </button>
        </div>
      </section>

      <section class="panel mini-panel">
        <header class="panel-head">
          <h3>我的足迹</h3>
          <router-link class="panel-more" to="/footprints">
            {{ footprintCount > 0 ? `全部 ${footprintCount} 件 ›` : '更多 ›' }}
          </router-link>
        </header>
        <div v-if="footprints.length === 0" class="mini-empty">
          <div class="mini-empty__icon">👣</div>
          <p>最近还没有浏览记录</p>
        </div>
        <div v-else class="mini-strip">
          <button
            v-for="f in footprints"
            :key="f.productId"
            type="button"
            class="mini-item"
            @click="goProduct(f.productId)"
          >
            <ProductThumb class="mini-item__thumb" :src="f.mainImage" :name="f.productName" :seed="f.productId" />
            <span class="mini-item__name">{{ f.productName }}</span>
          </button>
        </div>
      </section>

      <section class="panel mini-panel">
        <header class="panel-head">
          <h3>购物车</h3>
          <router-link class="panel-more" to="/cart">查看 ›</router-link>
        </header>
        <div class="cart-mini">
          <div class="cart-mini__num">{{ cartCount }}</div>
          <div class="cart-mini__label">件商品待结算</div>
          <router-link to="/cart" class="cart-mini__btn">去结算</router-link>
        </div>
      </section>
    </div>

    <!-- 猜你喜欢 -->
    <section class="panel like-panel">
      <header class="panel-head">
        <h3>猜你喜欢</h3>
        <router-link class="panel-more" to="/products">更多好物 ›</router-link>
      </header>
      <div v-if="likes.length === 0" class="like-empty">暂无推荐</div>
      <div v-else class="like-grid">
        <GoodsCard
          v-for="p in likes"
          :key="p.productId"
          :product="p"
          @click="goDetail(p.productId)"
        />
      </div>
    </section>

    <div class="mine-tools">
      <router-link to="/chat" class="mine-tool">💬 在线客服</router-link>
      <router-link to="/custom" class="mine-tool">🎨 我的定制</router-link>
      <router-link to="/refunds" class="mine-tool">↩️ 退款售后</router-link>
      <button class="mine-tool mine-tool--danger" @click="logout">退出登录</button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 个人中心（对齐「我的淘宝」版式）：资料卡 + 订单五状态 + 收藏/足迹/购物车三联卡 + 猜你喜欢。
 *
 * 数据全部走真接口：`GET /api/me` 取资料、`GET /api/orders` 拉本人订单后在前端按状态计数
 * （用户端订单接口不分页筛选状态，客户自己的订单量级小，一次拉全比五个请求更省）；
 * 收藏 / 足迹 / 售后角标各自走 `/favorites`、`/footprints`、`/refunds/count`——
 * 2026-11-16 之前这三处分别是写死空态、写死空态、`Boolean(o.refundStatus)` 计出的 0。
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { OrderStatus, statusLabel, type UserMeVO } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { profileApi } from '@/api/profile';
import { fileApi } from '@/api/file';
import { orderApi, type OrderListItem } from '@/api/order';
import { productApi, type CatalogProductVO } from '@/api/product';
import { favoriteApi, type FavoriteVO } from '@/api/favorite';
import { footprintApi, type FootprintVO } from '@/api/footprint';
import { refundApi } from '@/api/refund';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { useCartStore } from '@/store/modules/useCartStore';
import GoodsCard from '@/components/GoodsCard.vue';
import ProductThumb from '@/components/ProductThumb.vue';

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

const cartCount = computed(() => cart.totalQty);
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

/** 收藏：三联卡只露最近 4 件，全部在 /favorites 里看 */
async function loadFavorites(): Promise<void> {
  if (!userId.value) return;
  try {
    const page = await unwrap(favoriteApi.list({ page: 1, size: 4 }));
    favorites.value = page?.list ?? [];
    favCount.value = Number(page?.total ?? 0);
  } catch {
    favorites.value = [];
    favCount.value = 0;
  }
}

/** 足迹：同上，最近 4 件 */
async function loadFootprints(): Promise<void> {
  if (!userId.value) return;
  try {
    const page = await unwrap(footprintApi.list({ page: 1, size: 4 }));
    footprints.value = page?.list ?? [];
    footprintCount.value = Number(page?.total ?? 0);
  } catch {
    footprints.value = [];
    footprintCount.value = 0;
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
  router.push({ path: '/orders', query: { status } });
}
function goDetail(id: string): void {
  router.push(`/product/${id}`);
}
function goProduct(productId: string | number): void {
  router.push(`/product/${productId}`);
}

async function logout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确认退出登录？', '提示', { type: 'warning' });
  } catch {
    return;
  }
  await auth.logout();
  router.replace('/login');
}

onMounted(async () => {
  await Promise.all([
    loadProfile(),
    loadOrders(),
    loadLikes(),
    loadFavorites(),
    loadFootprints(),
    loadRefundCount(),
  ]);
  if (auth.isLoggedIn) {
    cart.load().catch(() => { /* 购物车失败不影响个人中心 */ });
  }
});
</script>

<style scoped>
.mine-page {
  width: var(--sent-container);
  max-width: calc(100% - 32px);
  margin: 0 auto;
  padding: 16px 0 32px;
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
  border-radius: 12px;
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
  background: rgba(255, 255, 255, 0.14);
  border-radius: 8px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.side-mini__label {
  font-size: 11px;
  opacity: 0.85;
}
.side-mini__num {
  font-size: 17px;
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
  background: #fff;
  border-radius: 10px;
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
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

/* ---------- 订单五状态 ---------- */
.order-stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
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
  gap: 4px;
  transition: background 0.15s;
}
.order-stat:hover {
  background: var(--sent-primary-soft);
}
.order-stat b {
  font-size: 20px;
  color: var(--sent-text);
  line-height: 1.1;
}
.order-stat__num--hot {
  color: var(--sent-accent-dark) !important;
}
.order-stat span {
  font-size: 12px;
  color: var(--sent-muted);
}

/* ---------- 三联卡 ---------- */
.mine-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}
.mini-panel {
  min-height: 148px;
}
.mini-empty {
  text-align: center;
  color: var(--sent-muted);
  font-size: 12px;
  padding: 12px 0 6px;
}
.mini-empty__icon {
  font-size: 30px;
  opacity: 0.35;
  margin-bottom: 6px;
}

/* 三联卡里的迷你商品条：横向 4 个，够看又不至于把卡片撑高 */
.mini-strip {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}
.mini-item {
  border: none;
  background: transparent;
  padding: 0;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
  text-align: left;
  min-width: 0;
}
.mini-item__thumb {
  width: 100%;
  height: 72px;
  border-radius: 6px;
  display: block;
}
.mini-item__name {
  font-size: 11px;
  color: var(--sent-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: 100%;
}
.mini-item:hover .mini-item__name {
  color: var(--sent-primary);
}
.cart-mini {
  text-align: center;
  padding: 6px 0 2px;
}
.cart-mini__num {
  font-size: 26px;
  font-weight: 700;
  color: var(--sent-accent-dark);
  line-height: 1.1;
}
.cart-mini__label {
  font-size: 12px;
  color: var(--sent-muted);
  margin-bottom: 10px;
}
.cart-mini__btn {
  display: inline-block;
  font-size: 12px;
  color: #fff;
  background: var(--sent-accent);
  border-radius: 14px;
  padding: 5px 18px;
}
.cart-mini__btn:hover {
  background: var(--sent-accent-dark);
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

/* ---------- 底部工具 ---------- */
.mine-tools {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.mine-tool {
  font-size: 13px;
  color: var(--sent-text-2);
  background: #fff;
  border: 1px solid var(--sent-border);
  border-radius: 16px;
  padding: 6px 14px;
  cursor: pointer;
}
.mine-tool:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}
.mine-tool--danger:hover {
  border-color: #e34d59;
  color: #e34d59;
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
