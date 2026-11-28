<template>
  <div class="cart-page">
    <!-- 顶部：标题 + 促销条 + 购物车内搜索（对齐参考图第一行） -->
    <div class="cart-head">
      <div class="cart-head__left">
        <h2 class="cart-head__title">购物车</h2>
        <span class="cart-head__promo">
          <b>会员日</b> 满 500 减 50 · 定制订单同享 · 企业账期可用
        </span>
      </div>
      <div class="cart-head__right">
        <input
          v-model="keyword"
          class="cart-search"
          placeholder="搜索购物车内商品"
          @keyup.enter="noop"
        />
        <span class="cart-head__count">共 {{ items.length }} 件</span>
      </div>
    </div>

    <el-empty v-if="items.length === 0" description="购物车是空的，去挑点装备吧">
      <el-button type="primary" @click="goProducts">去逛逛</el-button>
    </el-empty>

    <div v-else class="cart-layout">
      <!-- 左：列表 -->
      <div class="cart-main">
        <!-- 工具栏 -->
        <div class="cart-toolbar">
          <el-checkbox :model-value="allSelected" @change="onSelectAll">全选</el-checkbox>
          <button class="link-btn" @click="moveToFavorite">移入收藏</button>
          <button class="link-btn link-btn--danger" :disabled="selectedItems.length === 0" @click="removeSelected">
            删除
          </button>
          <div class="cart-toolbar__right">
            <select v-model="sortBy" class="cart-select">
              <option value="default">默认排序</option>
              <option value="priceAsc">单价从低到高</option>
              <option value="priceDesc">单价从高到低</option>
              <option value="qtyDesc">数量从多到少</option>
            </select>
            <select v-model="qtyFilter" class="cart-select">
              <option value="all">全部数量</option>
              <option value="multi">2 件及以上</option>
              <option value="single">仅 1 件</option>
            </select>
          </div>
        </div>

        <!-- 店铺分组：本项目单店铺，标题对应参考图的「淘宝/天猫」位置 -->
        <div class="shop-group">
          <div class="shop-head">
            <span class="shop-badge">自营</span>
            <b class="shop-name">森特体育自营店</b>
            <span class="shop-tag">UV 定制</span>
            <span class="shop-tag">企业团购</span>
          </div>

          <div v-if="visibleItems.length === 0" class="cart-empty-line">没有符合条件的商品</div>

          <div v-for="it in visibleItems" :key="it.itemId" class="cart-row">
            <el-checkbox
              class="cart-row__check"
              :model-value="selectedIds.includes(it.itemId)"
              @change="() => onToggle(it.itemId)"
            />

            <!-- 购物车只持有 skuId，商品详情页按 productId 打开；这里不做错误跳转 -->
            <div class="cart-thumb">
              <ProductThumb :name="it.skuName" :seed="it.skuId" />
            </div>

            <div class="cart-info">
              <div class="cart-info__title">{{ it.skuName || `商品规格 #${it.skuId}` }}</div>
              <div class="cart-info__attrs">
                <span>规格：{{ specText(it.skuName) }}</span>
                <span>货号：SKU-{{ it.skuId }}</span>
              </div>
              <div class="cart-info__services">
                <span class="svc">7 天无理由</span>
                <span class="svc">极速退款</span>
                <span class="svc">包邮</span>
              </div>
            </div>

            <div class="cart-price">
              <div class="cart-price__now">
                <em>¥</em>{{ priceText(it.skuId) }}
              </div>
              <div class="cart-price__old">¥{{ originalText(it.skuId) }}</div>
              <div class="cart-price__save">省 ¥{{ saveText(it.skuId) }}</div>
            </div>

            <div class="cart-qty">
              <button class="qty-btn" :disabled="it.qty <= 1" @click="onQty(it.itemId, it.qty - 1)">-</button>
              <input class="qty-input" :value="it.qty" readonly />
              <button class="qty-btn" :disabled="it.qty >= 99" @click="onQty(it.itemId, it.qty + 1)">+</button>
            </div>

            <div class="cart-ops">
              <button class="link-btn" @click="moveToFavorite">移入收藏</button>
              <button class="link-btn link-btn--danger" @click="onRemove(it.itemId)">删除</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右：结算明细（对齐参考图右侧固定卡） -->
      <aside class="cart-aside">
        <div class="aside-card">
          <h3 class="aside-title">结算明细</h3>

          <div v-if="selectedItems.length === 0" class="aside-empty">
            <div class="aside-empty__icon">🛒</div>
            <p>选择商品查看实际支付价格</p>
          </div>

          <template v-else>
            <div class="aside-line">
              <span>已选商品</span><span>{{ selectedItems.length }} 种 / {{ selectedQty }} 件</span>
            </div>
            <div class="aside-line">
              <span>商品金额</span><span>{{ goodsAmountText }}</span>
            </div>
            <div class="aside-line">
              <span>运费</span><span class="aside-muted">结算页计算</span>
            </div>
            <div class="aside-line aside-line--total">
              <span>合计</span>
              <b class="aside-total">{{ goodsAmountText }}</b>
            </div>
          </template>

          <button
            class="aside-pay"
            :disabled="selectedItems.length === 0"
            @click="goCheckout"
          >
            结算{{ selectedItems.length ? `（${selectedQty}）` : '' }}
          </button>
          <p class="aside-hint">支持企业账期与增值税专用发票，结算页可选</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 购物车（对齐参考图版式：左列表 + 右结算明细固定卡）。
 *
 * 数据口径：
 * - 行单价来自取价引擎 `/cart/checkout-preview`（按全量购物车试算），
 *   展示价与结算价同源，前端不自己乘系数；
 * - 右侧合计只累加「已勾选」行的商品金额，运费与优惠留给结算页，避免与下单口径打架。
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { fmtMoney } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { cartApi, type QuoteLineVO } from '@/api/cart';
import { useCartStore } from '@/store/modules/useCartStore';
import ProductThumb from '@/components/ProductThumb.vue';

const router = useRouter();
const cart = useCartStore();

const keyword = ref('');
const sortBy = ref('default');
const qtyFilter = ref('all');
const lines = ref<Record<number, QuoteLineVO>>({});

const items = computed(() => cart.items);
const selectedIds = computed(() => cart.selectedIds);
const selectedItems = computed(() => cart.selectedItems);
const selectedQty = computed(() => selectedItems.value.reduce((s, i) => s + i.qty, 0));
const allSelected = computed(
  () => items.value.length > 0 && selectedIds.value.length === items.value.length,
);

const visibleItems = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  let list = items.value.filter((it) => {
    if (kw && !String(it.skuName ?? '').toLowerCase().includes(kw)) return false;
    if (qtyFilter.value === 'multi') return it.qty >= 2;
    if (qtyFilter.value === 'single') return it.qty === 1;
    return true;
  });
  if (sortBy.value !== 'default') {
    list = [...list].sort((a, b) => {
      if (sortBy.value === 'qtyDesc') return b.qty - a.qty;
      const pa = unitCent(a.skuId);
      const pb = unitCent(b.skuId);
      return sortBy.value === 'priceAsc' ? pa - pb : pb - pa;
    });
  }
  return list;
});

const goodsAmountText = computed(() => {
  const cent = selectedItems.value.reduce((sum, it) => {
    const line = lines.value[it.skuId];
    return sum + (line ? Number(line.lineTotalCent) : 0);
  }, 0);
  return fmtMoney(cent);
});

function unitCent(skuId: number): number {
  return Number(lines.value[skuId]?.unitPriceCent ?? 0);
}
function priceText(skuId: number): string {
  const line = lines.value[skuId];
  return line ? String(line.unitPriceYuan) : '--';
}
function originalText(skuId: number): string {
  // 划线价 = 零售指导价（取整加价 10%），仅作展示对比
  const line = lines.value[skuId];
  if (!line) return '--';
  return (Number(line.unitPriceCent) * 11 / 10 / 100).toFixed(2);
}
function saveText(skuId: number): string {
  const line = lines.value[skuId];
  if (!line) return '--';
  return (Number(line.unitPriceCent) / 10 / 100).toFixed(2);
}
function specText(name: string): string {
  const parts = String(name ?? '').split(/[\s/]+/).filter(Boolean);
  return parts.length > 1 ? parts.slice(1).join(' / ') : '默认规格';
}

function onToggle(id: number): void {
  cart.toggleSelect(id);
}
function onSelectAll(): void {
  cart.selectAll(!allSelected.value);
}

async function onQty(id: number, qty: number): Promise<void> {
  const next = Math.min(99, Math.max(1, qty));
  await cartApi.update(id, next, selectedIds.value.includes(id));
  const it = cart.items.find((i) => i.itemId === id);
  if (it) it.qty = next;
  await refreshPrices();
}

async function onRemove(id: number): Promise<void> {
  await cart.remove(id);
  await refreshPrices();
}

async function removeSelected(): Promise<void> {
  if (selectedItems.value.length === 0) return;
  const ids = selectedItems.value.map((i) => i.itemId);
  for (const id of ids) {
    await cart.remove(id);
  }
  ElMessage.success(`已删除 ${ids.length} 件商品`);
  await refreshPrices();
}

function moveToFavorite(): void {
  // 收藏夹尚未建设：给出明确说明而不是假装成功
  ElMessage.info('收藏夹功能尚未开放，商品先留在购物车');
}

function noop(): void {
  /* 搜索实时过滤，回车无需额外动作 */
}

function goProducts(): void {
  router.push('/products');
}
function goCheckout(): void {
  router.push('/checkout');
}

/** 按全量购物车试算单价：保证每行都有展示价，不只针对已勾选项 */
async function refreshPrices(): Promise<void> {
  if (items.value.length === 0) {
    lines.value = {};
    return;
  }
  try {
    const quote = await unwrap(cartApi.checkoutPreview({
      availablePoints: 0,
      freightFreeThresholdCent: 0,
      freightFeeCent: 0,
      items: items.value.map((i) => ({
        skuId: i.skuId,
        qty: i.qty,
        customerTypeCode: 'PERSON',
        memberDiscountRate: 100,
        agreementDiscountRate: 100,
        allowStacking: false,
        ladderTiers: [],
      })),
    }));
    const map: Record<number, QuoteLineVO> = {};
    for (const line of quote?.lines ?? []) map[Number(line.skuId)] = line;
    lines.value = map;
  } catch {
    lines.value = {};
  }
}

onMounted(async () => {
  await cart.load();
  await refreshPrices();
});
</script>

<style scoped>
.cart-page {
  /* 与顶部导航同一个版心，否则标题会顶到浏览器左边缘 */
  width: var(--sent-container);
  max-width: calc(100% - 32px);
  margin: 0 auto;
  padding-bottom: 28px;
}

/* ---------- 顶部 ---------- */
.cart-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 0 12px;
}
.cart-head__left {
  display: flex;
  align-items: baseline;
  gap: 14px;
  min-width: 0;
}
.cart-head__title {
  margin: 0;
  font-size: 22px;
  color: var(--sent-text);
}
.cart-head__promo {
  font-size: 13px;
  color: var(--sent-accent-dark);
  background: var(--sent-accent-soft);
  border: 1px solid #ffdcbf;
  border-radius: 14px;
  padding: 3px 12px;
}
.cart-head__promo b {
  margin-right: 4px;
}
.cart-head__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}
.cart-search {
  width: 220px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--sent-border);
  border-radius: 16px;
  outline: none;
  font-size: 13px;
}
.cart-search:focus {
  border-color: var(--sent-primary);
}
.cart-head__count {
  font-size: 13px;
  color: var(--sent-muted);
}

/* ---------- 布局 ---------- */
.cart-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  align-items: start;
}

/* ---------- 工具栏 ---------- */
.cart-toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  background: #fff;
  border-radius: var(--sent-radius) var(--sent-radius) 0 0;
  border-bottom: 1px solid #f2f3f5;
}
.cart-toolbar__right {
  margin-left: auto;
  display: flex;
  gap: 8px;
}
.cart-select {
  height: 30px;
  border: 1px solid var(--sent-border);
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  color: var(--sent-text-2);
  padding: 0 8px;
  outline: none;
}

/* ---------- 店铺分组 ---------- */
.shop-group {
  background: #fff;
  border-radius: 0 0 var(--sent-radius) var(--sent-radius);
  padding: 0 16px 6px;
}
.shop-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 0 10px;
  border-bottom: 1px solid #f5f6f7;
}
.shop-badge {
  font-size: 11px;
  color: #fff;
  background: var(--sent-accent);
  border-radius: 3px;
  padding: 1px 6px;
}
.shop-name {
  font-size: 14px;
  color: var(--sent-text);
}
.shop-tag {
  font-size: 11px;
  color: var(--sent-primary);
  background: var(--sent-primary-soft);
  border-radius: 10px;
  padding: 1px 8px;
}

/* ---------- 行 ---------- */
.cart-row {
  display: grid;
  grid-template-columns: 24px 88px minmax(0, 1fr) 120px 96px 78px;
  gap: 12px;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid #f7f8f9;
}
.cart-row:last-child {
  border-bottom: none;
}
.cart-thumb {
  display: block;
  width: 88px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #f2f3f5;
}
.cart-info__title {
  font-size: 14px;
  color: var(--sent-text);
  line-height: 1.5;
  margin-bottom: 6px;
}
.cart-info__title:hover {
  color: var(--sent-accent-dark);
}
.cart-info__attrs {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--sent-muted);
  margin-bottom: 6px;
}
.cart-info__services {
  display: flex;
  gap: 6px;
}
.svc {
  font-size: 11px;
  color: #8a6a52;
  border: 1px solid #f0dfd3;
  border-radius: 3px;
  padding: 0 5px;
}

.cart-price__now {
  font-size: 16px;
  font-weight: 700;
  color: var(--sent-price);
}
.cart-price__now em {
  font-style: normal;
  font-size: 12px;
  margin-right: 1px;
}
.cart-price__old {
  font-size: 12px;
  color: #b9bfc8;
  text-decoration: line-through;
}
.cart-price__save {
  font-size: 11px;
  color: var(--sent-accent-dark);
}

.cart-qty {
  display: flex;
  align-items: center;
  height: 28px;
  border: 1px solid var(--sent-border);
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
}
.qty-btn {
  width: 26px;
  height: 100%;
  border: none;
  background: #fafbfc;
  color: var(--sent-text-2);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}
.qty-btn:hover:not(:disabled) {
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
}
.qty-btn:disabled {
  color: #d4d7dc;
  cursor: not-allowed;
}
.qty-input {
  width: 40px;
  height: 100%;
  border: none;
  border-left: 1px solid var(--sent-border);
  border-right: 1px solid var(--sent-border);
  text-align: center;
  font-size: 13px;
  outline: none;
}

.cart-ops {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
}
.link-btn {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 12px;
  color: var(--sent-text-2);
  cursor: pointer;
}
.link-btn:hover {
  color: var(--sent-primary);
}
.link-btn--danger:hover {
  color: #e34d59;
}
.link-btn:disabled {
  color: #c8ccd2;
  cursor: not-allowed;
}

.cart-empty-line {
  padding: 40px 0;
  text-align: center;
  color: var(--sent-muted);
  font-size: 13px;
}

/* ---------- 右侧结算明细 ---------- */
.cart-aside {
  position: sticky;
  top: 16px;
}
.aside-card {
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}
.aside-title {
  margin: 0 0 14px;
  font-size: 15px;
  color: var(--sent-text);
  padding-bottom: 10px;
  border-bottom: 1px solid #f2f3f5;
}
.aside-empty {
  text-align: center;
  padding: 22px 0 18px;
  color: var(--sent-muted);
  font-size: 12px;
}
.aside-empty__icon {
  font-size: 40px;
  opacity: 0.35;
  margin-bottom: 8px;
}
.aside-line {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: var(--sent-text-2);
  padding: 5px 0;
}
.aside-muted {
  color: var(--sent-muted);
}
.aside-line--total {
  margin-top: 8px;
  padding-top: 10px;
  border-top: 1px dashed #ececec;
  align-items: baseline;
}
.aside-total {
  font-size: 22px;
  color: var(--sent-accent-dark);
}
.aside-pay {
  width: 100%;
  margin-top: 14px;
  height: 42px;
  border: none;
  border-radius: 21px;
  background: var(--sent-accent);
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s;
}
.aside-pay:hover:not(:disabled) {
  background: var(--sent-accent-dark);
}
.aside-pay:disabled {
  background: #f3d3bd;
  cursor: not-allowed;
}
.aside-hint {
  margin: 10px 0 0;
  font-size: 11px;
  color: var(--sent-muted);
  line-height: 1.5;
}

@media (max-width: 1080px) {
  .cart-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .cart-aside {
    position: static;
  }
  .cart-row {
    grid-template-columns: 24px 72px minmax(0, 1fr) 110px 90px 70px;
  }
}
</style>
