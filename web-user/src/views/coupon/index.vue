<template>
  <div class="coupon-page">
    <h2 class="coupon-title">我的优惠券</h2>
    <p class="coupon-hint">注册礼、平台活动与售后补偿发放的优惠券都在这里，结算时可直接选用。</p>

    <div class="coupon-tabs">
      <button
        v-for="t in tabs"
        :key="t.key"
        class="coupon-tab"
        :class="{ 'coupon-tab--active': active === t.key }"
        @click="active = t.key"
      >{{ t.label }}（{{ counts[t.key] }}）</button>
    </div>

    <div v-if="loading" class="coupon-empty">加载中…</div>
    <div v-else-if="visible.length === 0" class="coupon-empty">这里还没有{{ tabLabel }}优惠券</div>
    <div v-else class="coupon-grid">
      <article
        v-for="c in visible"
        :key="c.couponId"
        class="coupon-card"
        :class="{ 'coupon-card--disabled': c.status !== 'UNUSED' }"
        data-testid="coupon-card"
      >
        <div class="coupon-card__amount">
          <template v-if="c.discountType === 'PERCENT'">{{ rateText(c) }}</template>
          <template v-else><span class="coupon-card__symbol">¥</span>{{ yuan(c.amountCent) }}</template>
        </div>
        <div class="coupon-card__info">
          <b>{{ c.name }}</b>
          <span class="coupon-card__threshold">{{ thresholdText(c.thresholdCent) }}</span>
          <span class="coupon-card__expire">有效期至 {{ fmtDateTime(Number(c.expireAtMillis)) }}</span>
          <span v-if="c.reason" class="coupon-card__reason">{{ c.reason }}</span>
        </div>
        <span class="coupon-card__status">{{ statusLabel(c.status) }}</span>
      </article>
    </div>

    <router-link to="/products" class="coupon-go">去挑选商品 ›</router-link>
  </div>
</template>

<script setup lang="ts">
/**
 * 我的优惠券（2026-11-18）：注册礼 / 平台活动 / 售后补券统一入口。
 * 券数据走 /api/coupons/mine，不在前端判断过期——过期态由服务端按 expire_at 派生。
 */
import { computed, onMounted, ref } from 'vue';
import { fmtDateTime } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { couponApi, type CouponVO } from '@/api/coupon';

type TabKey = 'UNUSED' | 'USED' | 'EXPIRED';
const tabs: { key: TabKey; label: string }[] = [
  { key: 'UNUSED', label: '待使用' },
  { key: 'USED', label: '已使用' },
  { key: 'EXPIRED', label: '已过期' },
];

const active = ref<TabKey>('UNUSED');
const loading = ref(false);
const coupons = ref<CouponVO[]>([]);

const counts = computed<Record<TabKey, number>>(() => ({
  UNUSED: coupons.value.filter((c) => c.status === 'UNUSED').length,
  USED: coupons.value.filter((c) => c.status === 'USED').length,
  EXPIRED: coupons.value.filter((c) => c.status === 'EXPIRED').length,
}));
const visible = computed(() => coupons.value.filter((c) => c.status === active.value));
const tabLabel = computed(() => tabs.find((t) => t.key === active.value)?.label ?? '');

function yuan(cent: number | string): string {
  const n = Number(cent ?? 0);
  return Number.isInteger(n / 100) ? String(n / 100) : (n / 100).toFixed(2);
}
function thresholdText(cent: number | string): string {
  const n = Number(cent ?? 0);
  return n > 0 ? `满 ${yuan(n)} 元可用` : '无门槛';
}
/** 折扣券按「支付比例」存，90 → 9折、88 → 8.8折 */
function rateText(c: CouponVO): string {
  const zhe = Number(c.discountRate ?? 100) / 10;
  return `${Number.isInteger(zhe) ? zhe : zhe.toFixed(1)}折`;
}
function statusLabel(status: string): string {
  return status === 'USED' ? '已使用' : status === 'EXPIRED' ? '已过期' : '待使用';
}

onMounted(async () => {
  loading.value = true;
  try {
    coupons.value = (await unwrap(couponApi.mine())) ?? [];
  } catch {
    coupons.value = [];
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.coupon-page {
  width: var(--sent-container);
  max-width: calc(100% - 32px);
  margin: 0 auto;
  padding: 22px 0 40px;
}
.coupon-title { margin: 0 0 6px; font-size: 22px; color: var(--sent-text); }
.coupon-hint { margin: 0 0 18px; font-size: 13px; color: var(--sent-muted); }
.coupon-tabs { display: flex; gap: 10px; margin-bottom: 16px; }
.coupon-tab {
  border: 1px solid var(--sent-border);
  background: #fff;
  color: var(--sent-text-2);
  border-radius: 18px;
  padding: 6px 16px;
  font-size: 13px;
  cursor: pointer;
}
.coupon-tab--active { border-color: var(--sent-accent); color: var(--sent-accent-dark); background: var(--sent-accent-soft); }
.coupon-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.coupon-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border: 1px solid #f4d8c2;
  border-radius: 10px;
  padding: 16px;
  min-width: 0;
}
.coupon-card--disabled { filter: grayscale(0.8); opacity: 0.66; }
.coupon-card__amount { font-size: 32px; font-weight: 700; color: var(--sent-accent-dark); flex: none; }
.coupon-card__symbol { font-size: 16px; margin-right: 2px; }
.coupon-card__info { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.coupon-card__info b { font-size: 14px; color: var(--sent-text); }
.coupon-card__threshold, .coupon-card__expire, .coupon-card__reason { font-size: 11px; color: var(--sent-muted); }
.coupon-card__status { position: absolute; top: 10px; right: 12px; font-size: 11px; color: var(--sent-accent-dark); }
.coupon-empty { padding: 46px 0; text-align: center; color: var(--sent-muted); font-size: 13px; }
.coupon-go { display: inline-block; margin-top: 20px; font-size: 13px; color: var(--sent-primary); }
@media (max-width: 900px) { .coupon-grid { grid-template-columns: 1fr; } }
</style>
