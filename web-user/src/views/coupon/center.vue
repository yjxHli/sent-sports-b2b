<template>
  <div class="center-page">
    <header class="center-hero">
      <div class="center-hero__main">
        <h1 class="center-hero__title">领券中心</h1>
        <p class="center-hero__sub">先领券再下单：无门槛券、满减券、品类券一次领齐，单笔订单限用 1 张。</p>
      </div>
      <router-link to="/coupons" class="center-hero__link">我的优惠券 ›</router-link>
    </header>

    <section class="center-section">
      <div class="center-section__head">
        <h2>可领取优惠券</h2>
        <span class="center-section__hint">
          {{ loading ? '加载中…' : `共 ${coupons.length} 张，${claimedCount} 张已领取` }}
        </span>
      </div>

      <div v-if="!loading && coupons.length === 0" class="center-empty">暂无可领取的优惠券</div>
      <div v-else class="claim-grid">
        <article
          v-for="c in coupons"
          :key="c.templateId"
          class="claim-card"
          :class="{ 'claim-card--done': c.claimed || c.remainQty === 0 }"
          data-testid="claim-card"
        >
          <div class="claim-card__left">
            <div class="claim-card__value">
              <span v-if="isPercent(c)" class="claim-card__rate">{{ rateText(c) }}</span>
              <template v-else>
                <span class="claim-card__symbol">¥</span>{{ yuan(c.amountCent) }}
              </template>
            </div>
            <div class="claim-card__cond">{{ thresholdText(c) }}</div>
          </div>
          <div class="claim-card__right">
            <b class="claim-card__name">{{ c.name }}</b>
            <div class="claim-card__tags">
              <span v-if="showTypeTag(c)" class="claim-card__tag">{{ typeText(c) }}</span>
              <span v-if="c.scopeType !== 'ALL'" class="claim-card__tag claim-card__tag--scope">
                {{ c.scopeType === 'SPU' ? '限指定商品' : '限指定规格' }}
              </span>
            </div>
            <span class="claim-card__valid">领取后 {{ c.validDays }} 天内有效</span>
            <button
              type="button"
              class="claim-card__btn"
              :class="{ 'claim-card__btn--done': c.claimed || c.remainQty === 0 }"
              :disabled="c.claimed || c.remainQty === 0 || claimingId === c.templateId"
              data-testid="claim-btn"
              @click="claim(c)"
            >
              {{ btnText(c) }}
            </button>
          </div>
        </article>
      </div>
    </section>

    <section class="center-section">
      <div class="center-section__head">
        <h2>用券更划算</h2>
        <router-link to="/products" class="center-section__more">查看全部商品 ›</router-link>
      </div>
      <div v-if="loadingGoods" class="center-empty">加载中…</div>
      <div v-else class="goods-grid">
        <GoodsCard
          v-for="p in products"
          :key="p.productId"
          :product="p"
          @click="openProduct(p)"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * 领券中心（2026-11-18）：上半区可领券、下半区商品，点首页「领券中心」进来。
 *
 * 数据全部走服务端：`GET /api/coupons/claimable` 已经按客户类型过滤（企业专属券
 * 不对个人客户展示）并回填 claimed；前端不重复判断领取资格，只负责渲染与防连点。
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { couponApi, type ClaimableVO } from '@/api/coupon';
import { productApi, type CatalogProductVO } from '@/api/product';
import GoodsCard from '@/components/GoodsCard.vue';

const router = useRouter();

const coupons = ref<ClaimableVO[]>([]);
const products = ref<CatalogProductVO[]>([]);
const loading = ref(true);
const loadingGoods = ref(true);
const claimingId = ref<number | null>(null);

const claimedCount = computed(() => coupons.value.filter((c) => c.claimed).length);

function num(v: number | string | null | undefined): number {
  return Number(v ?? 0);
}
function yuan(cent: number | string): string {
  const n = num(cent);
  return Number.isInteger(n / 100) ? String(n / 100) : (n / 100).toFixed(2);
}
function isPercent(c: ClaimableVO): boolean {
  return c.discountType === 'PERCENT';
}
/** 90 → 9折；88 → 8.8折 */
function rateText(c: ClaimableVO): string {
  const rate = num(c.discountRate);
  const zhe = rate / 10;
  return `${Number.isInteger(zhe) ? zhe : zhe.toFixed(1)}折`;
}
function thresholdText(c: ClaimableVO): string {
  const n = num(c.thresholdCent);
  if (n > 0) return `满 ${yuan(n)} 元可用`;
  return isPercent(c) ? '无门槛折扣' : '无门槛';
}
function typeText(c: ClaimableVO): string {
  switch (c.couponType) {
    case 'NO_THRESHOLD':
      return '无门槛券';
    case 'FULL_REDUCTION':
      return '满减券';
    case 'CATEGORY':
      return '品类券';
    case 'ENTERPRISE':
      return '企业专属券';
    default:
      return '优惠券';
  }
}
function btnText(c: ClaimableVO): string {
  if (c.claimed) return '已领取';
  if (c.remainQty === 0) return '已领完';
  return claimingId.value === c.templateId ? '领取中…' : '立即领取';
}
/** 券名里已经写了券型（如「5元无门槛券」）就不再挂一枚同义标签 */
function showTypeTag(c: ClaimableVO): boolean {
  return !c.name.includes(typeText(c));
}

async function claim(c: ClaimableVO): Promise<void> {
  if (c.claimed || c.remainQty === 0 || claimingId.value !== null) return;
  claimingId.value = c.templateId;
  try {
    await unwrap(couponApi.claim(c.templateId));
    c.claimed = true;
    // 后端已扣减库存，本地同步减一，避免必须整页重拉才更新「已领完」
    if (c.remainQty > 0) c.remainQty -= 1;
    ElMessage.success('领取成功，可在「我的优惠券」查看');
  } catch (e) {
    ElMessage.error((e as Error)?.message || '领取失败，请稍后再试');
  } finally {
    claimingId.value = null;
  }
}

function openProduct(p: CatalogProductVO): void {
  void router.push(`/product/${p.productId}`);
}

async function loadCoupons(): Promise<void> {
  try {
    coupons.value = (await unwrap(couponApi.claimable())) ?? [];
  } catch {
    coupons.value = [];
  } finally {
    loading.value = false;
  }
}

async function loadProducts(): Promise<void> {
  try {
    // 用券商品区只做导流，取销量最高的前 10 个即可，不引分页
    const page = await unwrap(productApi.list({ page: 1, size: 10 }));
    products.value = page?.list ?? [];
  } catch {
    products.value = [];
  } finally {
    loadingGoods.value = false;
  }
}

onMounted(() => {
  void loadCoupons();
  void loadProducts();
});
</script>

<style scoped>
.center-page {
  width: var(--sent-container);
  max-width: calc(100% - 32px);
  margin: 0 auto;
  padding: 20px 0 40px;
}
.center-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  background: linear-gradient(135deg, #ff5a2b 0%, #ff8a3d 100%);
  color: #fff;
  border-radius: 12px;
  padding: 20px 22px;
}
.center-hero__title {
  margin: 0;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: 1px;
}
.center-hero__sub {
  margin: 8px 0 0;
  font-size: 13px;
  opacity: 0.94;
}
.center-hero__link {
  flex: none;
  font-size: 13px;
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.7);
  border-radius: 16px;
  padding: 5px 14px;
}
.center-section {
  margin-top: 22px;
}
.center-section__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}
.center-section__head h2 {
  margin: 0;
  font-size: 17px;
  color: var(--sent-text);
}
.center-section__hint,
.center-section__more {
  font-size: 12px;
  color: var(--sent-muted);
}
.center-empty {
  padding: 36px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
  background: #fff;
  border-radius: 10px;
}
.claim-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.claim-card {
  display: flex;
  align-items: stretch;
  background: #fff;
  border: 1px solid #f4d8c2;
  border-radius: 10px;
  overflow: hidden;
  min-width: 0;
}
.claim-card--done {
  filter: grayscale(0.7);
  opacity: 0.8;
}
.claim-card__left {
  flex: none;
  width: 116px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  background: #fff6ef;
  border-right: 1px dashed #f4d8c2;
  padding: 14px 8px;
}
.claim-card__value {
  font-size: 30px;
  font-weight: 800;
  color: var(--sent-accent-dark);
  line-height: 1.1;
  white-space: nowrap;
}
.claim-card__symbol {
  font-size: 15px;
  margin-right: 1px;
}
.claim-card__rate {
  font-size: 26px;
}
.claim-card__cond {
  font-size: 11px;
  color: var(--sent-muted);
  white-space: nowrap;
}
.claim-card__right {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 14px 14px 12px;
}
.claim-card__name {
  font-size: 14px;
  color: var(--sent-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.claim-card__tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.claim-card__tag {
  font-size: 11px;
  color: var(--sent-accent-dark);
  background: var(--sent-accent-soft);
  border-radius: 4px;
  padding: 1px 6px;
  white-space: nowrap;
}
.claim-card__tag--scope {
  color: #1677ff;
  background: #eef5ff;
}
.claim-card__valid {
  font-size: 11px;
  color: var(--sent-muted);
}
.claim-card__btn {
  margin-top: auto;
  align-self: flex-start;
  border: none;
  border-radius: 15px;
  background: var(--sent-accent);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  padding: 6px 18px;
  cursor: pointer;
}
.claim-card__btn--done {
  background: #e5e6eb;
  color: var(--sent-muted);
  cursor: not-allowed;
}
.goods-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}
@media (max-width: 1100px) {
  .claim-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .goods-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@media (max-width: 900px) {
  .claim-grid { grid-template-columns: 1fr; }
  .goods-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
</style>
