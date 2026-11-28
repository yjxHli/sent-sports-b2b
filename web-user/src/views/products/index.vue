<template>
  <div class="container category-page">
    <!-- 结果头：分类名 + 件数 + 店铺/全部商品入口 -->
    <div class="cat-result">
      <div class="cat-result__main">
        <h1 class="cat-result__title">{{ pageTitle }}</h1>
        <span class="cat-result__count">{{ countText }}</span>
      </div>
      <div class="cat-result__meta">
        <span class="cat-result__shop">🏪 森特体育自营店</span>
        <a class="cat-result__all" @click="switchCategory(null)">全部商品 ›</a>
      </div>
    </div>

    <!-- 分类楼层条（淘宝式图标楼层，本页内切换分类） -->
    <div class="floor-bar">
      <span class="floor-bar__label">分类</span>
      <button
        v-for="c in categories"
        :key="c.categoryId"
        type="button"
        class="cat-chip"
        :class="{ 'cat-chip--active': String(c.categoryId) === String(categoryId) }"
        @click="switchCategory(String(c.categoryId))"
      >
        <span class="cat-chip__icon">{{ iconOf(c.name) }}</span>
        <span class="cat-chip__name">{{ c.name }}</span>
      </button>
    </div>

    <!-- 店铺 tab 行（对齐淘宝「所有宝贝 / 天猫 / 店铺」的层级切换） -->
    <div class="shop-tabs">
      <a class="shop-tab shop-tab--active">全部宝贝</a>
      <router-link to="/group-buy" class="shop-tab">企业团购</router-link>
      <router-link to="/custom" class="shop-tab">定制服务</router-link>
      <router-link to="/orders" class="shop-tab">我的订单</router-link>
      <span class="shop-tabs__hint">森特体育自营 · 正品保障 · 30 天无理由退换</span>
    </div>

    <!-- 排序 + 价格区间 + 结果内搜索 -->
    <div class="cat-sort">
      <button
        type="button"
        class="cat-sort__tab"
        :class="{ 'cat-sort__tab--active': sortBy === 'default' }"
        @click="setSort('default')"
      >
        综合
      </button>
      <button
        type="button"
        class="cat-sort__tab"
        :class="{ 'cat-sort__tab--active': sortBy === 'sales' }"
        @click="setSort('sales')"
      >
        销量
      </button>
      <button
        type="button"
        class="cat-sort__tab"
        :class="{ 'cat-sort__tab--active': sortBy === 'price' }"
        @click="togglePrice"
      >
        价格
        <span class="cat-sort__arrow">{{ priceAsc ? '↑' : '↓' }}</span>
      </button>

      <div class="price-range">
        <input v-model="minYuan" class="price-range__input" placeholder="¥ 最低" @keyup.enter="applyPrice" />
        <span class="price-range__sep">—</span>
        <input v-model="maxYuan" class="price-range__input" placeholder="¥ 最高" @keyup.enter="applyPrice" />
        <button type="button" class="price-range__btn" @click="applyPrice">确定</button>
      </div>

      <div class="cat-sort__search">
        <el-input
          v-model="keyword"
          placeholder="在结果中搜索"
          clearable
          @keyup.enter="applyKeyword"
          @clear="applyKeyword"
        >
          <template #append>
            <el-button @click="applyKeyword">搜索</el-button>
          </template>
        </el-input>
      </div>
    </div>

    <!-- 服务筛选 + 发货地 -->
    <div class="filter-bar">
      <button
        type="button"
        class="filter-chip"
        :class="{ 'filter-chip--active': onlyInStock }"
        @click="onlyInStock = !onlyInStock"
      >
        现货
      </button>
      <button
        type="button"
        class="filter-chip"
        :class="{ 'filter-chip--active': onlyMember }"
        @click="onlyMember = !onlyMember"
      >
        会员价
      </button>
      <button
        type="button"
        class="filter-chip"
        :class="{ 'filter-chip--active': onlyAgreement }"
        @click="onlyAgreement = !onlyAgreement"
      >
        企业协议价
      </button>
      <button v-if="hasFilter" type="button" class="filter-chip filter-chip--reset" @click="resetFilters">
        清空筛选 ✕
      </button>
      <div class="filter-bar__right">
        <span class="filter-bar__ship">发货地：全国</span>
        <span class="filter-bar__page">第 {{ page }} 页 / 共 {{ pageCount }} 页</span>
      </div>
    </div>

    <div v-if="loading" class="list-hint">加载中…</div>

    <!-- 搜索合规空状态：40004 不是报错，不弹红色提示、不渲染「暂无商品」 -->
    <div v-else-if="illegalSearch" class="compliance-tip" data-testid="illegal-search-tip">
      <div class="compliance-tip__shield" aria-hidden="true">
        <svg viewBox="0 0 48 48" fill="none">
          <path d="M24 4 40 10v12c0 10.2-6.8 18.7-16 22-9.2-3.3-16-11.8-16-22V10L24 4Z"
                fill="var(--sent-primary)" />
          <path d="m17 24 5 5 10-11" stroke="#fff" stroke-width="3.4" stroke-linecap="round"
                stroke-linejoin="round" />
        </svg>
      </div>
      <p class="compliance-tip__text">系统提示：您的搜索词违反相关法律法规，请文明上网。</p>
      <router-link to="/home" class="compliance-tip__back" data-testid="illegal-search-back">返回首页</router-link>
    </div>

    <el-empty v-else-if="displayProducts.length === 0" description="没有符合条件的商品" />

    <div v-if="!loading && !illegalSearch" class="goods-grid">
      <GoodsCard
        v-for="p in displayProducts"
        :key="p.productId"
        :product="p"
        shop="森特体育自营店"
        show-from
        new-tab
        testid="list-good-card"
      />
    </div>

    <!-- 无限滚动哨兵 -->
    <div ref="sentinel" class="scroll-sentinel" aria-hidden="true"></div>
    <div v-if="loadingMore" class="list-hint">正在加载更多…</div>
    <div v-else-if="finished && displayProducts.length > 0" class="list-end">
      — 没有更多了 —
    </div>

    <!-- 右侧悬浮工具条（对齐淘宝右侧浮条） -->
    <div class="side-tool">
      <router-link to="/chat" target="_blank" rel="noopener" class="side-tool__item">
        <span class="side-tool__icon">💬</span><i>客服</i>
      </router-link>
      <router-link to="/orders" class="side-tool__item">
        <span class="side-tool__icon">📦</span><i>订单</i>
      </router-link>
      <router-link to="/cart" class="side-tool__item">
        <span class="side-tool__icon">🛒</span><i>购物车</i>
      </router-link>
      <button type="button" class="side-tool__item" @click="toTop">
        <span class="side-tool__icon">↑</span><i>顶部</i>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 分类结果页（仿淘宝分类页）：点首页分类后在新标签页打开本页。
 *
 * 版式（对齐淘宝男装分类页）：结果头 → 分类图标楼层 → 店铺 tab 行 →
 * 综合/销量/价格 + 价格区间 + 结果内搜索 → 服务标签与发货地行 → 5 列商品网格 → 右侧悬浮工具条。
 *
 * 数据：`GET /api/products?categoryId=&keyword=&page=&size=`（后端只支持这两个筛选条件）。
 * 服务标签（现货 / 会员价 / 企业协议价）与价格区间都是**本地过滤**已加载的数据，
 * 不伪造后端不存在的筛选参数。
 *
 * 排序说明：后端未开放排序参数，为保证「全局有序」而不是「仅当前页有序」，
 * 切到销量/价格时一次性取 `size=100`（`PageQuery.MAX_SIZE`）在前端排序；
 * 「综合」则维持后端顺序 + 无限滚动增量加载。
 */
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { HttpError, unwrap } from '@/utils/http';
import { yuanToCent } from '@sent/shared';
import {
  productApi,
  type CatalogProductVO,
  type CatalogSkuVO,
  type CategoryVO,
} from '@/api/product';
import GoodsCard from '@/components/GoodsCard.vue';

const route = useRoute();
const router = useRouter();

const PAGE_SIZE = 12;
/** 搜索命中违禁词：后端 ErrorCode.ILLEGAL_SEARCH_KEYWORD，前端改渲染合规空状态 */
const ILLEGAL_SEARCH_KEYWORD = 40004;
/** 排序时一次性拉全的上限（= PageQuery.MAX_SIZE） */
const MAX_FETCH = 100;
const PRELOAD_PX = 240;

const categories = ref<CategoryVO[]>([]);
const categoryId = ref<string | undefined>(
  route.query.categoryId ? String(route.query.categoryId) : undefined,
);
const keyword = ref<string>((route.query.keyword as string) || '');

const products = ref<CatalogProductVO[]>([]);
const illegalSearch = ref(false);
const loading = ref(false);
const loadingMore = ref(false);
const finished = ref(false);
const page = ref(1);
const total = ref(0);
const sentinel = ref<HTMLElement | null>(null);
let observer: IntersectionObserver | undefined;

type SortKey = 'default' | 'sales' | 'price';
const sortBy = ref<SortKey>('default');
const priceAsc = ref(true);

/** 服务筛选（本地过滤，全部基于真实字段） */
const onlyInStock = ref(false);
const onlyMember = ref(false);
const onlyAgreement = ref(false);

/** 价格区间：输入框按「元」填，比较时转「分」 */
const minYuan = ref('');
const maxYuan = ref('');
const minCent = ref<number | null>(null);
const maxCent = ref<number | null>(null);

const hasFilter = computed(
  () => onlyInStock.value || onlyMember.value || onlyAgreement.value || minCent.value !== null || maxCent.value !== null,
);

const pageTitle = computed(() => {
  if (categoryId.value) {
    const hit = categories.value.find((c) => String(c.categoryId) === String(categoryId.value));
    return hit ? hit.name : '全部商品';
  }
  return keyword.value ? `搜索「${keyword.value}」` : '全部商品';
});

/** 分类楼层图标：按分类名映射，未命中用通用标签图标 */
const CATEGORY_ICONS: Record<string, string> = {
  球类运动: '🎽',
  足球: '⚽',
  篮球: '🏀',
  羽毛球: '🏸',
  跑步: '👟',
  健身: '🏋️',
  瑜伽: '🧘',
  游泳: '🏊',
  户外: '⛰️',
  服饰: '👕',
};

function iconOf(name: string): string {
  const hit = Object.entries(CATEGORY_ICONS).find(([k]) => name.includes(k));
  return hit ? hit[1] : '🏷️';
}

function skuOf(p: CatalogProductVO): CatalogSkuVO | undefined {
  return p.skus?.find((s) => s.onShelf) ?? p.skus?.[0];
}

function minPrice(p: CatalogProductVO): number {
  return skuOf(p)?.price.retailPriceCent ?? 0;
}

/** 有值才算「提供」——`JacksonConfig` 把 Long 序列化成字符串，0 也要按空处理 */
function hasPrice(p: CatalogProductVO, key: 'memberPriceCent' | 'agreementPriceCent'): boolean {
  const v = skuOf(p)?.price?.[key];
  return v !== null && v !== undefined && Number(v) > 0;
}

const filtered = computed<CatalogProductVO[]>(() =>
  products.value.filter((p) => {
    if (onlyInStock.value && !(Number(skuOf(p)?.stockQty ?? 0) > 0)) return false;
    if (onlyMember.value && !hasPrice(p, 'memberPriceCent')) return false;
    if (onlyAgreement.value && !hasPrice(p, 'agreementPriceCent')) return false;
    const price = minPrice(p);
    if (minCent.value !== null && price < minCent.value) return false;
    if (maxCent.value !== null && price > maxCent.value) return false;
    return true;
  }),
);

const displayProducts = computed<CatalogProductVO[]>(() => {
  const list = [...filtered.value];
  if (sortBy.value === 'sales') {
    list.sort((a, b) => Number(b.salesCount ?? 0) - Number(a.salesCount ?? 0));
  } else if (sortBy.value === 'price') {
    list.sort((a, b) => (minPrice(a) - minPrice(b)) * (priceAsc.value ? 1 : -1));
  }
  return list;
});

/** 结果头件数：筛选生效时补充筛选后条数，避免「说 13 件却只看到 3 件」 */
const countText = computed(() => {
  const base = `共 ${total.value} 件商品`;
  if (!hasFilter.value) return base;
  return `${base}（筛选后 ${displayProducts.value.length} 件）`;
});

/** 分页信息（对齐淘宝的 1/100 页码行） */
const pageCount = computed(() => (total.value > 0 ? Math.ceil(total.value / PAGE_SIZE) : 1));

function baseQuery() {
  return {
    keyword: keyword.value || undefined,
    categoryId: categoryId.value ? Number(categoryId.value) : null,
  };
}

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
    const res = await unwrap(productApi.list({ ...baseQuery(), page: target, size: PAGE_SIZE }));
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

/** 排序模式：一次性取全量（后端按固定顺序返回，排序在前端做才是全局有序） */
async function fetchAll(): Promise<void> {
  loading.value = true;
  try {
    const res = await unwrap(productApi.list({ ...baseQuery(), page: 1, size: MAX_FETCH }));
    products.value = res.list ?? [];
    total.value = Number(res.total ?? 0);
    finished.value = true;
  } finally {
    loading.value = false;
  }
}

function sentinelNear(): boolean {
  const el = sentinel.value;
  if (!el) return false;
  return el.getBoundingClientRect().top <= window.innerHeight + PRELOAD_PX;
}

async function loadMore(): Promise<void> {
  if (loading.value || loadingMore.value || finished.value) return;
  // 排序模式下数据已全量在手，不再追加
  if (sortBy.value !== 'default') return;
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
  if (sentinelNear()) void loadMore();
}

async function loadFirst(): Promise<void> {
  products.value = [];
  illegalSearch.value = false;
  page.value = 1;
  total.value = 0;
  finished.value = false;
  try {
    if (sortBy.value === 'default') await fetchPage(1, false);
    else await fetchAll();
  } catch (e) {
    illegalSearch.value = e instanceof HttpError && e.code === ILLEGAL_SEARCH_KEYWORD;
    finished.value = !illegalSearch.value;
  }
  await nextTick();
  await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
  if (!illegalSearch.value && sortBy.value === 'default' && sentinelNear()) void loadMore();
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

/** 滚动兜底：哨兵长期停在预加载区时 IntersectionObserver 不会再有交叉事件 */
let scrollRaf = 0;
function onWindowScroll(): void {
  if (scrollRaf) return;
  scrollRaf = requestAnimationFrame(() => {
    scrollRaf = 0;
    if (sentinelNear()) void loadMore();
  });
}

function setSort(key: SortKey): void {
  if (sortBy.value === key) return;
  sortBy.value = key;
  void loadFirst();
}

function togglePrice(): void {
  if (sortBy.value === 'price') {
    priceAsc.value = !priceAsc.value;
    void loadFirst();
  } else {
    sortBy.value = 'price';
    priceAsc.value = true;
    void loadFirst();
  }
}

/** 价格区间（元 → 分）：空值按未设置，非法输入忽略 */
function applyPrice(): void {
  const toCent = (raw: string): number | null => {
    // 换算逻辑走 shared；这里只保留「不允许负价格」的业务约束
    const n = yuanToCent(raw);
    return n === null || n < 0 ? null : n;
  };
  const lo = toCent(minYuan.value);
  const hi = toCent(maxYuan.value);
  // 上下限填反了就自动对调，别让用户看到空列表
  minCent.value = lo !== null && hi !== null ? Math.min(lo, hi) : lo;
  maxCent.value = lo !== null && hi !== null ? Math.max(lo, hi) : hi;
}

function resetFilters(): void {
  onlyInStock.value = false;
  onlyMember.value = false;
  onlyAgreement.value = false;
  minYuan.value = '';
  maxYuan.value = '';
  minCent.value = null;
  maxCent.value = null;
}

/** 切换分类/关键词：只改 query，由 watch 统一触发重新加载 */
function switchCategory(id: string | null): void {
  const query: Record<string, string> = {};
  if (id) query.categoryId = id;
  if (keyword.value) query.keyword = keyword.value;
  router.push({ path: '/products', query });
}

function applyKeyword(): void {
  const query: Record<string, string> = {};
  if (categoryId.value) query.categoryId = String(categoryId.value);
  if (keyword.value) query.keyword = keyword.value;
  router.push({ path: '/products', query });
}

function toTop(): void {
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

watch(
  () => [route.query.categoryId, route.query.keyword],
  () => {
    categoryId.value = route.query.categoryId ? String(route.query.categoryId) : undefined;
    keyword.value = (route.query.keyword as string) || '';
    void loadFirst();
  },
);

onMounted(async () => {
  try {
    categories.value = (await unwrap(productApi.categories())) ?? [];
  } catch {
    categories.value = [];
  }
  await loadFirst();
  await nextTick();
  setupObserver();
  window.addEventListener('scroll', onWindowScroll, { passive: true });
});

onUnmounted(() => {
  window.removeEventListener('scroll', onWindowScroll);
  if (scrollRaf) cancelAnimationFrame(scrollRaf);
  observer?.disconnect();
  observer = undefined;
});
</script>

<style scoped>
.category-page {
  padding-top: 14px;
}

/* ---------- 结果头 ---------- */
.cat-result {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 16px 18px 12px;
  margin-bottom: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.cat-result__main {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}
.cat-result__title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  color: var(--sent-text);
  letter-spacing: 0.5px;
}
.cat-result__title::before {
  content: '';
  display: inline-block;
  width: 4px;
  height: 18px;
  border-radius: 2px;
  background: var(--sent-accent);
  margin-right: 10px;
  vertical-align: -3px;
}
.cat-result__count {
  font-size: 13px;
  color: var(--sent-muted);
}
.cat-result__meta {
  display: flex;
  align-items: center;
  gap: 14px;
  flex: none;
  font-size: 13px;
  color: var(--sent-muted);
}
.cat-result__all {
  color: var(--sent-primary);
  cursor: pointer;
}
.cat-result__all:hover {
  color: var(--sent-accent);
}

/* ---------- 分类楼层条 ---------- */
.floor-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 12px 16px;
  margin-bottom: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow-x: auto;
}
.floor-bar__label {
  flex: none;
  font-size: 13px;
  color: var(--sent-muted);
  padding-right: 6px;
  border-right: 1px solid var(--sent-border);
  margin-right: 4px;
}
.cat-chip {
  flex: none;
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 8px;
  padding: 6px 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.cat-chip__icon {
  font-size: 20px;
  line-height: 1.1;
}
.cat-chip__name {
  font-size: 12px;
  color: var(--sent-text-2);
  white-space: nowrap;
}
.cat-chip:hover {
  background: var(--sent-primary-soft);
}
.cat-chip:hover .cat-chip__name {
  color: var(--sent-primary);
}
.cat-chip--active {
  background: var(--sent-primary-soft);
  border-color: var(--sent-primary);
}
.cat-chip--active .cat-chip__name {
  color: var(--sent-primary);
  font-weight: 700;
}

/* ---------- 店铺 tab 行 ---------- */
.shop-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 0 14px;
  margin-bottom: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.shop-tab {
  position: relative;
  padding: 12px 16px;
  font-size: 14px;
  color: var(--sent-text-2);
  cursor: pointer;
  transition: color 0.15s;
}
.shop-tab:hover {
  color: var(--sent-primary);
}
.shop-tab--active {
  color: var(--sent-primary);
  font-weight: 700;
}
.shop-tab--active::after {
  content: '';
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 0;
  height: 2px;
  border-radius: 2px;
  background: var(--sent-primary);
}
.shop-tabs__hint {
  margin-left: auto;
  font-size: 12px;
  color: var(--sent-muted);
}

/* ---------- 排序 + 价格区间 + 搜索 ---------- */
.cat-sort {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 8px 14px;
  margin-bottom: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.cat-sort__tab {
  border: none;
  background: transparent;
  color: var(--sent-text-2);
  font-size: 14px;
  padding: 6px 14px;
  border-radius: 6px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  transition: all 0.15s;
}
.cat-sort__tab:hover {
  color: var(--sent-primary);
}
.cat-sort__tab--active {
  background: var(--sent-primary);
  color: #fff;
  font-weight: 600;
}
.cat-sort__tab--active:hover {
  color: #fff;
}
.cat-sort__arrow {
  font-size: 12px;
  opacity: 0.6;
}
.cat-sort__tab--active .cat-sort__arrow {
  opacity: 1;
}

/* 价格区间 */
.price-range {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 6px;
  padding-left: 10px;
  border-left: 1px solid var(--sent-border);
}
.price-range__input {
  width: 74px;
  border: 1px solid var(--sent-border);
  border-radius: 4px;
  padding: 5px 8px;
  font-size: 12px;
  color: var(--sent-text);
  outline: none;
  transition: border-color 0.15s;
}
.price-range__input:focus {
  border-color: var(--sent-primary);
}
.price-range__sep {
  color: var(--sent-muted);
  font-size: 12px;
}
.price-range__btn {
  border: 1px solid var(--sent-border);
  background: #fafbfc;
  color: var(--sent-text-2);
  font-size: 12px;
  border-radius: 4px;
  padding: 5px 10px;
  cursor: pointer;
}
.price-range__btn:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}

.cat-sort__search {
  margin-left: auto;
  width: 280px;
}

/* ---------- 服务筛选 + 发货地 ---------- */
.filter-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 8px 14px;
  margin-bottom: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.filter-chip {
  border: 1px solid var(--sent-border);
  background: #fff;
  color: var(--sent-text-2);
  font-size: 12px;
  border-radius: 4px;
  padding: 4px 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.filter-chip:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}
.filter-chip--active {
  background: var(--sent-primary);
  border-color: var(--sent-primary);
  color: #fff;
  font-weight: 600;
}
.filter-chip--active:hover {
  color: #fff;
}
.filter-chip--reset {
  border-style: dashed;
  color: var(--sent-muted);
}
.filter-bar__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 12px;
  color: var(--sent-muted);
}
.filter-bar__ship {
  padding-right: 14px;
  border-right: 1px solid var(--sent-border);
}

/* ---------- 商品网格：列数由全局 .goods-grid 自适应（1440 版心 → 6 列） ---------- */

/* ---------- 右侧悬浮工具条 ---------- */
.side-tool {
  position: fixed;
  right: 12px;
  top: 45%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  gap: 1px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
  overflow: hidden;
  z-index: 20;
}
.side-tool__item {
  width: 52px;
  padding: 9px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  border: none;
  background: #fff;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.side-tool__item i {
  font-style: normal;
  font-size: 11px;
  color: var(--sent-text-2);
}
.side-tool__icon {
  font-size: 16px;
  line-height: 1.2;
}
.side-tool__item:hover {
  background: var(--sent-primary-soft);
}
.side-tool__item:hover i {
  color: var(--sent-primary);
}

/* ---------- 无限滚动 ---------- */
.scroll-sentinel {
  height: 1px;
}
.list-hint {
  padding: 18px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}
.list-end {
  padding: 20px 0 6px;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
  letter-spacing: 1px;
}

/* ---------- 搜索合规空状态（40004）：绿色盾牌 + 文明上网引导 ---------- */
.compliance-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 320px;
  padding: 40px 16px 56px;
  text-align: center;
}
.compliance-tip__shield {
  width: 76px;
  height: 76px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--sent-primary-soft);
}
.compliance-tip__shield svg {
  width: 44px;
  height: 44px;
}
.compliance-tip__text {
  margin: 18px 0 20px;
  font-size: 15px;
  line-height: 1.7;
  color: var(--sent-text-2);
}
.compliance-tip__back {
  display: inline-block;
  padding: 9px 24px;
  border-radius: 6px;
  background: var(--sent-primary);
  color: #fff;
  font-size: 14px;
  text-decoration: none;
}
.compliance-tip__back:hover {
  background: var(--sent-primary-dark);
}
</style>
