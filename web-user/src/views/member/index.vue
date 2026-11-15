<template>
  <div class="container member-page">
    <div v-if="loading" class="list-hint">加载中…</div>

    <template v-else>
      <!-- 等级主视觉：头像 + 头衔 + 成长值进度 + 有效期 -->
      <section class="member-hero">
        <div class="member-hero__left">
          <div class="member-hero__avatar">{{ avatarChar }}</div>
          <div class="member-hero__who">
            <div class="member-hero__name">{{ nickname }}</div>
            <span class="member-hero__level">{{ levelName }}</span>
          </div>
        </div>

        <div class="member-hero__growth">
          <div class="member-hero__growth-top">
            <span>成长值 {{ level?.growthValue ?? 0 }}</span>
            <span class="member-hero__gap">{{ gapText }}</span>
          </div>
          <el-progress
            :percentage="progressPercent"
            :stroke-width="10"
            color="#ffb877"
            :show-text="false"
          />
          <div class="member-hero__scale">
            <span>{{ level?.currentThreshold ?? 0 }}</span>
            <span>{{ level?.nextThreshold ?? 0 }}</span>
          </div>
        </div>

        <div class="member-hero__valid">有效期至 {{ validText }}</div>
      </section>

      <!-- 四项数据概览 -->
      <section class="member-stats">
        <div v-for="s in stats" :key="s.label" class="member-stat">
          <b class="member-stat__value">{{ s.value }}</b>
          <span class="member-stat__label">{{ s.label }}</span>
        </div>
      </section>

      <!-- 等级权益 -->
      <section class="member-card">
        <div class="member-card__head">
          <h3>当前等级权益</h3>
          <span class="member-card__sub">{{ levelName }} 专享</span>
        </div>
        <div class="member-benefits">
          <div v-for="b in benefits" :key="b.title" class="member-benefit">
            <span class="member-benefit__icon">{{ b.icon }}</span>
            <div class="member-benefit__text">
              <b>{{ b.title }}</b>
              <p>{{ b.desc }}</p>
            </div>
          </div>
        </div>
      </section>

      <!-- 专属商品 -->
      <section class="member-card member-goods">
        <div class="member-card__head">
          <h3>会员专属商品</h3>
          <a class="member-card__more" @click="goProducts">去逛逛 ›</a>
        </div>
        <el-empty v-if="exclusive.length === 0" description="暂无专属商品" :image-size="70" />
        <div v-else class="member-goods__list">
          <div
            v-for="p in exclusive"
            :key="p.productId"
            class="member-good"
            @click="goProduct(p.productId)"
          >
            <div class="member-good__cover">
              <ProductThumb
                class="member-good__thumb"
                :src="p.mainImage"
                :name="p.productName"
                :seed="p.productId"
              />
              <span class="member-good__badge" :class="p.source === 'AGREEMENT' ? 'member-good__badge--deal' : ''">
                {{ p.source === 'AGREEMENT' ? '专属价' : '会员专享' }}
              </span>
            </div>
            <div class="member-good__info">
              <div class="member-good__title">{{ p.productName }}</div>
              <div class="member-good__price">¥{{ p.priceYuan }}</div>
            </div>
          </div>
        </div>
      </section>

      <!-- 快捷入口 -->
      <section class="member-links">
        <router-link v-for="l in LINKS" :key="l.to" :to="l.to" class="member-link">
          <span class="member-link__icon">{{ l.icon }}</span>
          <span class="member-link__text">{{ l.label }}</span>
        </router-link>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 会员中心（重新设计）：等级主视觉 + 数据概览 + 等级权益 + 专属商品 + 快捷入口。
 * 数据全部来自既有只读接口：`/member/level`、`/member/points`、`/member/credit`、
 * `/member/exclusive-products`，昵称取 `GET /api/me`。
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { fmtDate, statusLabel, type UserMeVO } from '@sent/shared';
import { unwrap } from '@/utils/http';
import {
  memberApi,
  type CreditVO,
  type ExclusiveProductVO,
  type LevelVO,
  type PointVO,
} from '@/api/member';
import { profileApi } from '@/api/profile';
import ProductThumb from '@/components/ProductThumb.vue';

const router = useRouter();

const loading = ref(false);
const level = ref<LevelVO | null>(null);
const points = ref<PointVO | null>(null);
const credit = ref<CreditVO | null>(null);
const exclusive = ref<ExclusiveProductVO[]>([]);
const profile = ref<UserMeVO | null>(null);

const LINKS = [
  { to: '/orders', label: '我的订单', icon: '📦' },
  { to: '/custom', label: '我的定制', icon: '🎨' },
  { to: '/refunds', label: '我的售后', icon: '↩️' },
  { to: '/chat', label: '在线客服', icon: '💬' },
];

const nickname = computed(() => profile.value?.nickname || '森特会员');
const avatarChar = computed(() => nickname.value.slice(0, 1));
const levelName = computed(() => statusLabel(level.value?.level ?? profile.value?.levelCode));
const progressPercent = computed(() =>
  Math.max(0, Math.min(100, Math.round(Number(level.value?.progress ?? 0) * 100))),
);

const gapText = computed(() => {
  const growth = Number(level.value?.growthValue ?? 0);
  const next = Number(level.value?.nextThreshold ?? 0);
  // 后端下一档门槛与本档相同时即最高等级；成长值已过门槛时不再算差额（旧实现会出 -11167）
  if (!Number.isFinite(next) || next <= 0 || growth >= next) return '已达最高等级';
  return `距下一等级还差 ${next - growth}`;
});

const validText = computed(() => {
  // 后端用毫秒时间戳传授级有效期，0 表示长期有效；Long 会被序列化成字符串，必须 Number() 归一，
  // 否则 "0" 为真值 → fmtDate(0) → 1970-01-01。
  const millis = Number(level.value?.validToMillis ?? 0);
  return Number.isFinite(millis) && millis > 0 ? fmtDate(millis) : '长期有效';
});

const stats = computed(() => [
  { label: '可用积分', value: formatNumber(points.value?.availablePoints) },
  { label: '成长值', value: formatNumber(level.value?.growthValue) },
  { label: '授信额度', value: `¥${credit.value?.creditLimitYuan || '0.00'}` },
  { label: '应收余额', value: `¥${credit.value?.receivableYuan || '0.00'}` },
]);

/** 等级权益：按等级名关联展示（业务口径说明文案，不涉及取数） */
const benefits = computed(() => {
  const isHigh = ['LV3', 'LV4', 'LV5'].includes(String(level.value?.level ?? ''));
  return [
    { icon: '🏷️', title: '会员专属价', desc: isHigh ? '下单自动享受会员折扣价' : '部分商品享会员价' },
    { icon: '⭐', title: '积分加速', desc: isHigh ? '每消费 1 元得 2 积分' : '每消费 1 元得 1 积分' },
    { icon: '🎧', title: '专属客服', desc: '会员通道优先接入人工客服' },
    { icon: '🎨', title: '定制优先', desc: 'UV 定制订单优先排产与审图' },
  ];
});

function formatNumber(value: number | null | undefined): string {
  const n = Number(value ?? 0);
  return Number.isFinite(n) ? n.toLocaleString('zh-CN') : '0';
}

function goProduct(id: string): void {
  router.push(`/product/${id}`);
}
function goProducts(): void {
  router.push('/products');
}

onMounted(async () => {
  loading.value = true;
  try {
    const [lv, pt, cr, ex] = await Promise.all([
      unwrap(memberApi.level()),
      unwrap(memberApi.points()),
      unwrap(memberApi.credit()),
      unwrap(memberApi.exclusiveProducts()),
    ]);
    level.value = lv ?? null;
    points.value = pt ?? null;
    credit.value = cr ?? null;
    exclusive.value = ex ?? [];
  } finally {
    loading.value = false;
  }
  try {
    profile.value = await unwrap(profileApi.me());
  } catch {
    profile.value = null;
  }
});
</script>

<style scoped>
.member-page {
  padding-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ---------- 等级主视觉 ---------- */
.member-hero {
  display: grid;
  grid-template-columns: minmax(0, 260px) minmax(0, 1fr) auto;
  align-items: center;
  gap: 24px;
  padding: 22px 26px;
  border-radius: 14px;
  color: #fff;
  background: linear-gradient(120deg, #12664f 0%, #1c8a6b 52%, #2fae86 100%);
  box-shadow: 0 8px 22px rgba(18, 102, 79, 0.22);
}
.member-hero__left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}
.member-hero__avatar {
  width: 56px;
  height: 56px;
  flex: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
  border: 2px solid rgba(255, 255, 255, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  font-weight: 700;
}
.member-hero__who {
  min-width: 0;
}
.member-hero__name {
  font-size: 18px;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.member-hero__level {
  display: inline-block;
  margin-top: 6px;
  font-size: 12px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  padding: 2px 10px;
}
.member-hero__growth {
  min-width: 0;
}
.member-hero__growth-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
  margin-bottom: 8px;
}
.member-hero__gap {
  font-size: 12px;
  opacity: 0.86;
}
.member-hero__scale {
  display: flex;
  justify-content: space-between;
  margin-top: 4px;
  font-size: 11px;
  opacity: 0.72;
  font-variant-numeric: tabular-nums;
}
.member-hero__valid {
  font-size: 12px;
  opacity: 0.86;
  white-space: nowrap;
}

/* ---------- 四项概览 ---------- */
.member-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 18px 0;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.member-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  border-right: 1px dashed #eef0f2;
}
.member-stat:last-child {
  border-right: none;
}
.member-stat__value {
  font-size: 22px;
  color: var(--sent-text);
  font-variant-numeric: tabular-nums;
}
.member-stat__label {
  font-size: 12px;
  color: var(--sent-muted);
}

/* ---------- 通用卡片 ---------- */
.member-card {
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 16px 18px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.member-card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}
.member-card__head h3 {
  margin: 0;
  font-size: 16px;
  color: var(--sent-text);
  padding-left: 12px;
  position: relative;
}
.member-card__head h3::before {
  content: '';
  position: absolute;
  left: 0;
  top: 3px;
  bottom: 3px;
  width: 4px;
  border-radius: 2px;
  background: var(--sent-accent);
}
.member-card__sub {
  font-size: 12px;
  color: var(--sent-muted);
}
.member-card__more {
  font-size: 13px;
  color: var(--sent-muted);
  cursor: pointer;
}
.member-card__more:hover {
  color: var(--sent-accent);
}

/* ---------- 权益 ---------- */
.member-benefits {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.member-benefit {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: #fafbfc;
  border: 1px solid #f2f3f5;
  border-radius: 10px;
  padding: 12px;
}
.member-benefit__icon {
  font-size: 20px;
  line-height: 1.2;
}
.member-benefit__text b {
  font-size: 14px;
  color: var(--sent-text);
}
.member-benefit__text p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--sent-muted);
  line-height: 1.5;
}

/* ---------- 专属商品 ---------- */
.member-goods__list {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}
.member-good {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border: 1px solid #f2f3f5;
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.member-good:hover {
  border-color: var(--sent-primary);
  box-shadow: 0 6px 16px rgba(18, 102, 79, 0.1);
}
.member-good__cover {
  position: relative;
}
.member-good__thumb {
  display: block;
  width: 100%;
  height: 108px;
  border-radius: 8px;
}
.member-good__badge {
  position: absolute;
  left: 0;
  top: 0;
  font-size: 10px;
  color: #fff;
  background: rgba(18, 102, 79, 0.86);
  border-radius: 8px 0 8px 0;
  padding: 2px 6px;
  white-space: nowrap;
  flex: none;
}
.member-good__badge--deal {
  background: rgba(227, 77, 89, 0.9);
}
.member-good__title {
  font-size: 13px;
  color: var(--sent-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.member-good__price {
  font-size: 15px;
  font-weight: 700;
  color: var(--sent-accent-dark);
}

/* ---------- 快捷入口 ---------- */
.member-links {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.member-link {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 16px 0;
  font-size: 14px;
  color: var(--sent-text-2);
  border: 1px solid #f2f3f5;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  transition: all 0.15s;
}
.member-link:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
  transform: translateY(-2px);
}
.member-link__icon {
  font-size: 18px;
}

.list-hint {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}

@media (max-width: 1240px) {
  .member-hero {
    grid-template-columns: minmax(0, 1fr);
  }
  .member-benefits,
  .member-goods__list,
  .member-links {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
