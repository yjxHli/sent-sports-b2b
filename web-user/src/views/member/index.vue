<template>
  <div class="container member-page">
    <div v-if="loading" class="list-hint">加载中…</div>

    <template v-else>
      <!-- 等级主视觉：左身份 / 中成长值 / 右有效期（2026-11-28 重做） -->
      <section class="member-hero">
        <div class="member-hero__left">
          <div class="member-hero__avatar">{{ avatarChar }}</div>
          <div class="member-hero__who">
            <div class="member-hero__name">{{ nickname }}</div>
            <div class="member-hero__badges">
              <span class="member-hero__level">{{ levelName }}</span>
              <span class="member-hero__tag">会员专享价 · 积分加速</span>
            </div>
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

        <div class="member-hero__right">
          <div class="member-hero__valid">有效期至 {{ validText }}</div>
          <router-link to="/orders" class="member-hero__cta">去下单赚成长值</router-link>
        </div>
      </section>

      <!-- 四项数据概览 -->
      <section class="member-stats">
        <div
          v-for="(s, i) in stats"
          :key="s.label"
          class="member-stat"
          :class="{ 'member-stat--accent': i === 0 }"
        >
          <span class="member-stat__label">{{ s.label }}</span>
          <b class="member-stat__value">{{ s.value }}</b>
          <span class="member-stat__hint">{{ s.hint }}</span>
        </div>
      </section>

      <div class="member-grid">
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
      </div>

      <!-- 专属商品 -->
      <section class="member-card member-goods">
        <div class="member-card__head">
          <h3>会员专属商品</h3>
          <a class="member-card__more" :href="resolveHref(router, '/products')" target="_blank" rel="noopener">去逛逛 ›</a>
        </div>
        <el-empty v-if="exclusive.length === 0" description="暂无专属商品" :image-size="70" />
        <div v-else class="member-goods__list">
          <a
            v-for="p in exclusive"
            :key="p.productId"
            class="member-good"
            :href="productHref(router, p.productId)"
            target="_blank"
            rel="noopener"
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
          </a>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 会员中心（2026-11-28 第四次改版）：等级主视觉 + 四项概览 + 四项权益一行 + 专属商品。
 *
 * 视觉口径与个人中心一致：白底、8px 圆角、不靠阴影分层，主色深绿 + 活力橙。
 * 数据全部来自既有只读接口：`/member/level`、`/member/points`、`/member/credit`、
 * `/member/exclusive-products`，昵称取 `GET /api/me`——这一版没有任何新接口。
 *
 * E2E 锚点：`.member-hero` / `.member-stat` / `.member-benefit` / `.member-goods`；
 * 旧的「常用入口」整块已删除，专属商品卡与「去逛逛」改用原生 `<a target="_blank">`。
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
import { resolveHref, productHref } from '@/utils/link';

const router = useRouter();

const loading = ref(false);
const level = ref<LevelVO | null>(null);
const points = ref<PointVO | null>(null);
const credit = ref<CreditVO | null>(null);
const exclusive = ref<ExclusiveProductVO[]>([]);
const profile = ref<UserMeVO | null>(null);

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
  { label: '可用积分', value: formatNumber(points.value?.availablePoints), hint: '下单可抵现' },
  { label: '成长值', value: formatNumber(level.value?.growthValue), hint: '消费累积' },
  { label: '授信额度', value: `¥${credit.value?.creditLimitYuan || '0.00'}`, hint: '企业账期' },
  { label: '应收余额', value: `¥${credit.value?.receivableYuan || '0.00'}`, hint: '待回款' },
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
  gap: 14px;
}

/* ---------- 等级主视觉 ---------- */
.member-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 300px) minmax(0, 1fr) auto;
  align-items: center;
  gap: 28px;
  padding: 26px 30px;
  border-radius: 12px;
  color: #fff;
  overflow: hidden;
  background:
    radial-gradient(120% 160% at 88% 8%, rgba(255, 255, 255, 0.18) 0%, rgba(255, 255, 255, 0) 55%),
    linear-gradient(118deg, #0f5743 0%, #14785a 52%, #23a37c 100%);
}
/* 右上角一枚大号水印环，避免纯色块显得空 */
.member-hero::after {
  content: '';
  position: absolute;
  right: -60px;
  top: -80px;
  width: 240px;
  height: 240px;
  border-radius: 50%;
  border: 26px solid rgba(255, 255, 255, 0.07);
  pointer-events: none;
}
.member-hero__left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
  position: relative;
  z-index: 1;
}
.member-hero__avatar {
  width: 56px;
  height: 56px;
  flex: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.22);
  border: 2px solid rgba(255, 255, 255, 0.55);
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
.member-hero__badges {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  flex-wrap: wrap;
}
.member-hero__level {
  font-size: 12px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.9);
  color: #0f5743;
  border-radius: 10px;
  padding: 2px 10px;
  white-space: nowrap;
}
.member-hero__tag {
  font-size: 11.5px;
  opacity: 0.85;
  white-space: nowrap;
}
.member-hero__growth {
  min-width: 0;
  position: relative;
  z-index: 1;
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
.member-hero__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  position: relative;
  z-index: 1;
}
.member-hero__valid {
  font-size: 12px;
  opacity: 0.86;
  white-space: nowrap;
}
.member-hero__cta {
  font-size: 12.5px;
  color: #0f5743;
  background: #fff;
  border-radius: 14px;
  padding: 5px 16px;
  white-space: nowrap;
}
.member-hero__cta:hover {
  background: #f0fff9;
}

/* ---------- 四项概览（四张独立小卡） ---------- */
.member-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.member-stat {
  background: #fff;
  border-radius: 8px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.member-stat__value {
  font-size: 22px;
  color: var(--sent-text);
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}
.member-stat__label {
  font-size: 12.5px;
  color: var(--sent-text-2);
}
.member-stat__hint {
  font-size: 11.5px;
  color: var(--sent-muted);
}
.member-stat--accent {
  background: var(--sent-primary-soft);
}
.member-stat--accent .member-stat__value {
  color: var(--sent-primary);
}

/* ---------- 等级权益独占一行（常用入口已删除） ---------- */
.member-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
  align-items: stretch;
}
.member-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px 18px 18px;
  /* 卡片与相邻卡等高时，内容区撑满，避免权益只有 4 格时下方空一截 */
  display: flex;
  flex-direction: column;
}
.member-card__head {
  flex: none;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
  gap: 10px;
}
.member-card__head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--sent-text);
}
.member-card__sub {
  font-size: 12px;
  color: var(--sent-muted);
}
.member-card__more {
  font-size: 12.5px;
  color: var(--sent-muted);
  text-decoration: none;
  cursor: pointer;
}
.member-card__more:hover {
  color: var(--sent-accent);
}

/* 权益：四项一行，图标圆底 */
.member-benefits {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  grid-auto-rows: 1fr;
  gap: 12px;
  flex: 1;
}
.member-benefit {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  background: #fafbfc;
  border-radius: 8px;
  padding: 14px;
}
.member-benefit__icon {
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #fff;
  display: grid;
  place-items: center;
  font-size: 17px;
  line-height: 1;
}
.member-benefit__text b {
  font-size: 13.5px;
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
  padding: 8px;
  border-radius: 8px;
  color: inherit;
  text-decoration: none;
  cursor: pointer;
  transition: background 0.15s, box-shadow 0.15s;
}
.member-good:hover {
  background: #fff;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.07);
}
.member-good__cover {
  position: relative;
  border-radius: 8px;
  overflow: hidden;
}
.member-good__thumb {
  display: block;
  width: 100%;
  height: 116px;
}
.member-good__thumb :deep(.thumb) {
  width: 100%;
  height: 100%;
  aspect-ratio: auto;
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
}
.member-good__badge--deal {
  background: rgba(227, 77, 89, 0.9);
}
.member-good__title {
  font-size: 12.5px;
  line-height: 1.45;
  color: var(--sent-text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 36px;
}
.member-good__price {
  font-size: 15px;
  font-weight: 700;
  color: var(--sent-price);
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
    gap: 16px;
  }
  .member-hero__right {
    align-items: flex-start;
    flex-direction: row;
    gap: 14px;
  }
  .member-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .member-benefits {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .member-goods__list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .member-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
