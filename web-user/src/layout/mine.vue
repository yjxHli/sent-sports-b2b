<template>
  <div class="me-shell">
    <!-- 站点顶栏（2026-11-27）：与站点大版式共用一份，个人中心不再缺这一条 -->
    <ShopTopbar />

    <!-- 个人中心标题行：左侧「森特 | 我的森特」，右侧搜索（版式对齐商品详情页的迷你搜索） -->
    <header class="me-head">
      <div class="me-head__inner">
        <div class="me-head__brand">
          <router-link to="/home" class="me-head__logo" data-testid="mine-brand">森特</router-link>
          <span class="me-head__sep">|</span>
          <span class="me-head__title" data-testid="mine-title">{{ pageTitle }}</span>
        </div>
        <form class="me-search" role="search" @submit.prevent="onSearch">
          <div class="me-search__box">
            <input
              v-model="keyword"
              class="me-search__input"
              data-testid="mine-search-input"
              placeholder="搜索商品 / 品牌"
              aria-label="搜索商品"
              @focus="onSearchFocus"
              @blur="onSearchBlur"
            />
            <!-- 历史搜索（2026-11-28）：和商城首页共用一份 localStorage 记录 -->
            <div v-if="historyOpen" class="me-history" data-testid="mine-search-history">
              <div class="me-history__head">
                <span>历史搜索</span>
                <button type="button" data-testid="mine-clear-history" @mousedown.prevent="clearHistory">清除</button>
              </div>
              <div v-if="history.length" class="me-history__list">
                <button
                  v-for="word in history"
                  :key="word"
                  type="button"
                  class="me-history__item"
                  data-testid="mine-history-item"
                  @mousedown.prevent="pickHistory(word)"
                >{{ word }}</button>
              </div>
              <p v-else class="me-history__empty">暂无历史搜索</p>
            </div>
          </div>
          <button class="me-search__btn" type="submit" data-testid="mine-search-btn">搜索</button>
        </form>
      </div>
    </header>

    <div class="me-body">
      <!-- 侧边导航：我的森特 / 购物车 / 订单中心 / 优惠券 / 足迹收藏 / 发票 / 售后 / 企业工具 -->
      <aside class="me-side">
        <nav class="me-nav" data-testid="mine-sidebar" aria-label="个人中心导航">
          <div v-for="item in NAV" :key="item.key" class="me-nav__group">
            <button
              v-if="item.children"
              type="button"
              class="me-nav__row"
              :class="{ 'me-nav__row--on': isGroupActive(item) }"
              :data-testid="`mine-nav-${item.key}`"
              :aria-expanded="!collapsed[item.key]"
              @click="toggle(item.key)"
            >
              <span class="me-nav__icon" aria-hidden="true"><NavIcon :name="item.icon" /></span>
              <span class="me-nav__label">{{ item.label }}</span>
              <!-- 2026-11-28：原来用 10px 的 ▾ 文字，字太小看不清；换成 14px 描边箭头 -->
              <svg
                class="me-nav__caret"
                :class="{ 'me-nav__caret--open': !collapsed[item.key] }"
                viewBox="0 0 16 16"
                fill="none"
                aria-hidden="true"
              >
                <path d="M4 6.5 8 10.5l4-4" stroke="currentColor" stroke-width="1.6"
                      stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </button>
            <router-link
              v-else
              :to="item.to!"
              class="me-nav__row"
              :class="{ 'me-nav__row--on': isActive(item.to!) }"
              :data-testid="`mine-nav-${item.key}`"
            >
              <span class="me-nav__icon" aria-hidden="true"><NavIcon :name="item.icon" /></span>
              <span class="me-nav__label">{{ item.label }}</span>
            </router-link>

            <div v-if="item.children && !collapsed[item.key]" class="me-nav__children">
              <router-link
                v-for="child in item.children"
                :key="child.to"
                :to="child.to"
                class="me-nav__child"
                :class="{ 'me-nav__child--on': isActive(child.to) }"
                :data-testid="`mine-nav-${child.key}`"
                :target="child.blank ? '_blank' : undefined"
                :rel="child.blank ? 'noopener' : undefined"
              >{{ child.label }}</router-link>
            </div>
          </div>
        </nav>
      </aside>

      <main class="me-main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 个人中心外壳（2026-11-27 用户要求，参考淘宝「我的淘宝」/ 京东「我的京东」）。
 *
 * 版式：站点顶栏 → 「森特 | 页面标题」+ 右侧搜索 → 左侧导航 + 右侧内容。
 * 自 2026-11-27 起商城端所有「我的 xx」页面（购物车 / 订单 / 定制 / 优惠券 / 足迹 / 收藏 /
 * 售后 / 发票 / 企业工具）都挂在这个外壳下，旧站点版式的对应路由已删除。
 *
 * 视觉口径（用户 2026-11-27 定稿）：
 * - 统一白底，不再用「卡片 + 阴影」分割，靠留白和字号分层
 * - 字号统一 14px；行高 40px（子项 36px）保证点击区域；圆角统一 8px
 * - 图标略小于文字（13 / 14），图标盒子固定 18px，图文间距恒为 10px
 * - 组间 14px、组内 2px —— 组间明显比组内松
 */
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import ShopTopbar from '@/components/ShopTopbar.vue';
import NavIcon from '@/components/NavIcon.vue';
import { openInNewTab } from '@/utils/link';

interface NavChild { key: string; label: string; to: string; blank?: boolean }
interface NavItem { key: string; label: string; icon: string; to?: string; children?: NavChild[] }

const route = useRoute();
const router = useRouter();
const keyword = ref('');

const NAV: NavItem[] = [
  { key: 'overview', label: '我的森特', icon: 'home', to: '/mine' },
  { key: 'cart', label: '我的购物车', icon: 'cart', to: '/cart' },
  {
    key: 'orders',
    label: '订单中心',
    icon: 'orders',
    children: [
      { key: 'orders-goods', label: '商品订单', to: '/orders' },
      { key: 'orders-custom', label: '定制订单', to: '/custom' },
      // 2026-11-28 用户要求：评价中心挂在订单中心下（待评价的入口也指到这里）
      { key: 'orders-reviews', label: '评价中心', to: '/mine/reviews' },
    ],
  },
  { key: 'coupons', label: '我的优惠券', icon: 'coupon', to: '/coupons' },
  {
    key: 'trace',
    label: '足迹收藏',
    icon: 'trace',
    children: [
      { key: 'footprints', label: '我的足迹', to: '/footprints' },
      { key: 'favorites', label: '商品收藏', to: '/favorites' },
    ],
  },
  { key: 'invoices', label: '我的发票', icon: 'invoice', to: '/mine/invoices' },
  { key: 'refunds', label: '我的售后', icon: 'refund', to: '/refunds' },
  // 2026-11-28：会员中心从站点顶栏挪进侧栏
  { key: 'member', label: '会员中心', icon: 'member', to: '/member' },
  {
    key: 'enterprise',
    label: '企业工具',
    icon: 'enterprise',
    children: [
      { key: 'enterprise-procurement', label: '企业采购', to: '/mine/enterprise/procurement' },
      { key: 'enterprise-credit', label: '我的授信', to: '/mine/enterprise/credit' },
      { key: 'enterprise-pay-later', label: '先采后付', to: '/mine/enterprise/pay-later' },
      { key: 'enterprise-contracts', label: '我的合同', to: '/mine/enterprise/contracts' },
      // 2026-11-28：团购专区也进企业工具，与其他子项一样同页跳转（落地页保留侧边栏）
      { key: 'group-buy', label: '团购专区', to: '/group-buy' },
    ],
  },
];

/**
 * 分组**默认全部收起**（2026-11-28 用户要求）；只有当前路由落在某个分组里时才自动展开那一组，
 * 免得进来看到「高亮的父级但看不到自己在哪」。用户手动点开/收起的行为优先，不会被路由变化覆盖。
 */
const collapsed = reactive<Record<string, boolean>>(
  Object.fromEntries(NAV.filter((i) => i.children).map((i) => [i.key, true])),
);

const pageTitle = computed(() => (route.meta.title as string | undefined) || '我的森特');

function isActive(to: string): boolean {
  return route.path === to;
}

function isGroupActive(item: NavItem): boolean {
  return Boolean(item.children?.some((child) => route.path === child.to));
}

function toggle(key: string): void {
  collapsed[key] = !collapsed[key];
}

/**
 * 落到某个分组的子页时自动展开这一组（用户要求的「默认关、按需开」：
 * 手动点开过的组会记住，不会因为路由变化被收回去）。
 */
function expandActiveGroup(): void {
  const group = NAV.find((item) => item.children?.some((child) => route.path === child.to));
  if (group) collapsed[group.key] = false;
}

watch(() => route.path, expandActiveGroup, { immediate: true });

/** 搜索与商城首页同款：商品列表新开标签页，个人中心原页保持不动。 */
function onSearch(): void {
  const kw = keyword.value.trim();
  rememberSearch(kw);
  closeHistory();
  openInNewTab(router, { path: '/products', query: kw ? { keyword: kw } : {} });
}

/* ---------- 搜索历史（2026-11-28 用户要求：与首页搜索同款，带历史记录下拉） ---------- */
/**
 * 与首页搜索共用同一个 key：用户在商城头部搜过的词，进个人中心也能直接点。
 * 只存最近 10 条，最新在前，重复词提到最前。
 */
const HISTORY_KEY = 'sent_search_history_user';
const HISTORY_MAX = 10;
const history = ref<string[]>(loadHistory());
const historyOpen = ref(false);

function loadHistory(): string[] {
  try {
    const parsed = JSON.parse(localStorage.getItem(HISTORY_KEY) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter((x) => typeof x === 'string').slice(0, HISTORY_MAX) : [];
  } catch {
    return [];
  }
}

function rememberSearch(word: string): void {
  const kw = word.trim();
  if (!kw) return;
  history.value = [kw, ...history.value.filter((h) => h !== kw)].slice(0, HISTORY_MAX);
  try { localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value)); } catch { /* 隐私模式忽略 */ }
}

function clearHistory(): void {
  history.value = [];
  try { localStorage.removeItem(HISTORY_KEY); } catch { /* 隐私模式忽略 */ }
}

function pickHistory(word: string): void {
  keyword.value = word;
  onSearch();
}

function closeHistory(): void {
  historyOpen.value = false;
}

/** 输入框失焦延迟关：下拉里的项用 mousedown.prevent，不会先触发 blur */
let blurTimer: ReturnType<typeof setTimeout> | undefined;
function onSearchFocus(): void {
  if (blurTimer) clearTimeout(blurTimer);
  history.value = loadHistory();
  historyOpen.value = true;
}
function onSearchBlur(): void {
  blurTimer = setTimeout(closeHistory, 150);
}
onBeforeUnmount(() => { if (blurTimer) clearTimeout(blurTimer); });
</script>

<style scoped>
/* 统一白底：个人中心不铺灰底，靠留白分层（对齐淘宝 / 京东「我的」页） */
.me-shell {
  min-height: 100vh;
  background: #fff;
  display: flex;
  flex-direction: column;
}

/* ---------- 标题行 ---------- */
.me-head {
  background: #fff;
}
.me-head__inner {
  width: var(--sent-container);
  max-width: calc(100% - 32px);
  margin: 0 auto;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}
.me-head__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.me-head__logo {
  font-size: 22px;
  font-weight: 800;
  letter-spacing: 1px;
  color: var(--sent-primary);
  line-height: 1;
}
.me-head__logo:hover {
  color: var(--sent-primary-dark);
}
.me-head__sep {
  color: #d7dbe0;
  font-size: 16px;
}
.me-head__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--sent-text);
  white-space: nowrap;
}

/* 搜索：与商品详情页 `.detail-mini-search` 完全同款（直角 6px 圆角、1px 边框、深绿按钮） */
.me-search {
  display: flex;
  align-items: stretch;
  width: 320px;
  max-width: 46vw;
  flex: 0 0 auto;
}
/* 输入框外面包一层，历史记录下拉以它为定位基准 */
.me-search__box {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
}
.me-search__input {
  flex: 1 1 auto;
  min-width: 0;
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--sent-border);
  border-right: none;
  border-radius: 6px 0 0 6px;
  background: #fff;
  color: var(--sent-text);
  font-size: 13px;
  outline: none;
}
.me-search__input:focus {
  border-color: var(--sent-primary);
}
.me-search__input::placeholder {
  color: var(--sent-muted);
}
.me-history {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  z-index: 30;
  background: #fff;
  border: 1px solid var(--sent-border);
  border-radius: 6px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.08);
  padding: 8px 10px 10px;
}
.me-history__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--sent-muted);
  margin-bottom: 6px;
}
.me-history__head button {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 12px;
  color: var(--sent-muted);
  cursor: pointer;
}
.me-history__head button:hover {
  color: var(--sent-primary);
}
.me-history__list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.me-history__item {
  border: none;
  background: #f5f6f7;
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  color: var(--sent-text-2);
  cursor: pointer;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.me-history__item:hover {
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
}
.me-history__empty {
  margin: 2px 0 4px;
  font-size: 12px;
  color: var(--sent-muted);
}
.me-search__btn {
  flex: 0 0 auto;
  min-width: 64px;
  padding: 0 14px;
  border: none;
  border-radius: 0 6px 6px 0;
  background: var(--sent-primary);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.me-search__btn:hover {
  filter: brightness(1.08);
}

/* ---------- 主体 ---------- */
.me-body {
  width: var(--sent-container);
  max-width: calc(100% - 32px);
  margin: 0 auto;
  padding: 4px 0 48px;
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

/* 侧栏：无卡片、无阴影，左右零外留白（行内 12px 对称内留白） */
.me-side {
  position: sticky;
  top: 16px;
  background: #fff;
  max-height: calc(100vh - 32px);
  overflow-y: auto;
}
.me-nav {
  display: flex;
  flex-direction: column;
}

/* 组间 14px（明显松于组内 2px） */
.me-nav__group + .me-nav__group {
  margin-top: 14px;
}

/* 一级行：高 40px（点击区域）、圆角 8px、左右内边距对称 12px */
.me-nav__row {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 40px;
  padding: 0 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  line-height: 20px;
  color: var(--sent-text-2);
  text-align: left;
  transition: background 0.15s, color 0.15s;
}
.me-nav__row:hover {
  background: #f5f7f6;
  color: var(--sent-primary);
}
.me-nav__row--on {
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
  font-weight: 600;
}

/* 图标盒子固定 18px（图标本体 15px，比 14px 文字略小），图文间距恒为 10px */
.me-nav__icon {
  flex: none;
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #9aa3af;
}
.me-nav__row:hover .me-nav__icon,
.me-nav__row--on .me-nav__icon {
  color: var(--sent-primary);
}
.me-nav__label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
/* 展开箭头：14px 描边 chevron（原来 10px 文字箭头，太小） */
.me-nav__caret {
  flex: none;
  width: 14px;
  height: 14px;
  color: #8b93a1;
  transform: rotate(-90deg);
  transition: transform 0.15s, color 0.15s;
}
.me-nav__row:hover .me-nav__caret,
.me-nav__row--on .me-nav__caret {
  color: var(--sent-primary);
}
.me-nav__caret--open {
  transform: rotate(0deg);
}

/* 子项：与一级文字同字号，左缩进 = 12 + 18 + 10（对齐一级文字左沿） */
.me-nav__children {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 2px;
  padding-left: 40px;
}
.me-nav__child {
  display: flex;
  align-items: center;
  height: 36px;
  padding: 0 12px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 20px;
  color: var(--sent-text-2);
  transition: background 0.15s, color 0.15s;
}
.me-nav__child:hover {
  background: #f5f7f6;
  color: var(--sent-primary);
}
.me-nav__child--on {
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
  font-weight: 600;
}

/* 内容区：内部页面若自带 `.container`，改由外壳控制宽度，避免双层收窄 */
.me-main {
  min-width: 0;
}
.me-main :deep(.container) {
  width: 100%;
  max-width: 100%;
  margin: 0;
}
/* 页面自带的大标题与外壳标题行重复（外壳已显示「森特 | 我的发票」这种），
   统一在这里收掉；各页原有的数量 / 操作 / 说明都保留。 */
.me-main :deep(.cart-head__title),
.me-main :deep(.coupon-title),
.me-main :deep(.fav-bar__title),
.me-main :deep(.fp-bar__title),
.me-main :deep(.rf-head__title),
.me-main :deep(.custom-hero__title),
.me-main :deep(.profile-edit__title) {
  display: none;
}

@media (max-width: 920px) {
  .me-head__inner {
    height: auto;
    padding: 10px 0;
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
  }
  .me-search {
    width: 100%;
    max-width: none;
  }
  .me-body {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }
  .me-side {
    position: static;
    max-height: none;
  }
  .me-nav {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0 12px;
  }
}
</style>
