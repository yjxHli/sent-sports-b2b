<template>
  <div class="rf-page">
    <!-- 顶部条 -->
    <section class="rf-head">
      <div class="rf-head__main">
        <h1 class="rf-head__title">我的售后</h1>
        <p class="rf-head__desc">
          退款 / 退货进度都在这里。待审核的申请可以联系客服加急，已驳回的可以重新申请。
        </p>
      </div>
      <div class="rf-head__side">
        <div class="rf-head__stat">
          <b>{{ counts.total || total }}</b>
          <span>全部售后</span>
        </div>
        <div class="rf-head__stat rf-head__stat--hot">
          <b>{{ inFlightCount }}</b>
          <span>处理中</span>
        </div>
        <router-link to="/orders" class="rf-head__link">去我的订单 ›</router-link>
      </div>    </section>

    <!-- 状态筛选 -->
    <div class="rf-tabs">
      <button
        v-for="f in FILTERS"
        :key="f.value"
        type="button"
        class="rf-tab"
        :class="{ 'rf-tab--on': active === f.value }"
        @click="select(f.value)"
      >
        {{ f.label }}
      </button>
    </div>

    <div v-if="loading && list.length === 0" class="rf-hint">加载中…</div>

    <div v-else-if="list.length === 0" class="rf-empty">
      <div class="rf-empty__icon">↩</div>
      <p>{{ active === '' ? '还没有售后记录' : '当前状态下没有售后单' }}</p>
      <router-link v-if="active === ''" to="/orders" class="rf-empty__btn">去看看我的订单</router-link>
    </div>

    <div v-else class="rf-list">
      <article v-for="r in list" :key="r.refundNo" class="rf-card">
        <!-- 卡头：单号 + 申请时间 + 状态 -->
        <header class="rf-card__head">
          <span class="rf-card__time">{{ timeText(r.createdAtMillis) }} 申请</span>
          <span class="rf-card__no">退款单号 {{ r.refundNo }}</span>
          <span class="rf-card__status" :class="`rf-card__status--${tone(r.status)}`">
            {{ label(r.status) }}
          </span>
        </header>

        <!-- 主体：商品 + 金额 + 操作 -->
        <div class="rf-card__body">
          <div class="rf-card__goods" @click="goProduct(r)">
            <div class="rf-card__thumb">
              <ProductThumb :name="r.itemName || '商品'" :seed="r.refundNo" />
            </div>
            <div class="rf-card__info">
              <div class="rf-card__name">{{ r.itemName || '订单商品' }}</div>
              <div class="rf-card__meta">
                <span>订单号 {{ r.orderNo }}</span>
                <span class="rf-card__qty">共 {{ Number(r.itemQty ?? 0) || 1 }} 件</span>
              </div>
              <div v-if="r.reason" class="rf-card__reason">原因：{{ r.reason }}</div>
            </div>
          </div>

          <div class="rf-card__money">
            <span class="rf-card__money-label">退款金额</span>
            <b>¥{{ r.amountYuan || fmtCent(Number(r.amountCent ?? 0)) }}</b>
          </div>

          <div class="rf-card__ops">
            <button type="button" class="rf-btn rf-btn--main" @click="goDetail(r.refundNo)">
              查看进度
            </button>
            <button type="button" class="rf-btn" @click="goOrder(r.orderNo)">订单详情</button>
            <button
              v-if="canReapply(r.status)"
              type="button"
              class="rf-btn"
              @click="reapply(r.orderNo)"
            >重新申请</button>
            <button type="button" class="rf-btn" @click="goChat">联系客服</button>
          </div>
        </div>

        <!-- 进度条：4 步，让用户一眼知道卡在哪 -->
        <footer class="rf-card__steps">
          <div
            v-for="(s, i) in stepsOf(r)"
            :key="s.label"
            class="rf-step"
            :class="{ 'rf-step--done': s.done, 'rf-step--now': s.now }"
          >
            <span class="rf-step__dot" />
            <span class="rf-step__label">{{ s.label }}</span>
            <span v-if="i < 3" class="rf-step__line" />
          </div>
        </footer>
      </article>
    </div>

    <div v-if="list.length < total" class="rf-more">
      <button type="button" class="rf-more__btn" :disabled="loading" @click="loadMore">
        {{ loading ? '加载中…' : '加载更多' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 我的售后（2026-11-16 重做 UI）。
 *
 * 改版前只有「单号 + 状态 + 金额」三行文字，用户看不出「这单退的是哪件商品、到哪一步了」。
 * 现在按淘宝「退款/售后」的版式：卡头（申请时间 + 单号 + 状态）+ 商品行（缩略图/商品名/件数/原因）
 * + 退款金额 + 操作按钮（查看进度 / 订单详情 / 重新申请 / 联系客服）+ 四步进度条。
 *
 * 数据来自 `GET /api/refunds`（后端 2026-11-16 起在列表里补了商品名、件数、申请时间、原因、
 * SPU 主键），前端不再为每张卡片回查订单。
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { fmtCent, statusLabel, statusTone, type StatusTone } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { refundApi, type RefundVO } from '@/api/refund';
import ProductThumb from '@/components/ProductThumb.vue';

const router = useRouter();

const PAGE_SIZE = 10;

const FILTERS = [
  { label: '全部', value: '' },
  { label: '待审核', value: 'PENDING' },
  { label: '退款中', value: 'REFUNDING' },
  { label: '已退款', value: 'REFUNDED' },
  { label: '已关闭', value: 'CLOSED' },
  { label: '退款失败', value: 'FAILED' },
];

/** 退款状态文案（FAILED 在共享映射里是「支付失败」，售后语境要改成「退款失败」） */
const REFUND_LABELS: Record<string, string> = {
  PENDING: '待审核',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
  CLOSED: '已关闭',
  FAILED: '退款失败',
};
const REFUND_TONES: Record<string, StatusTone> = {
  PENDING: 'warning',
  REFUNDING: 'warning',
  REFUNDED: 'success',
  CLOSED: 'info',
  FAILED: 'danger',
};

const list = ref<RefundVO[]>([]);
const total = ref(0);
const page = ref(1);
const loading = ref(false);
const active = ref('');

/**
 * 「处理中」数量取后端的 `/api/refunds/count`（待审核 + 退款中），
 * 不用当前页数据数——翻页后当前页只剩 10 条，数出来的数字会跟着筛选跳。
 */
const counts = ref({ total: 0, inFlight: 0 });
const inFlightCount = computed(() => counts.value.inFlight);

async function loadCounts(): Promise<void> {
  try {
    const res = await unwrap(refundApi.count());
    counts.value = {
      total: Number(res?.total ?? 0),
      inFlight: Number(res?.inFlight ?? 0),
    };
  } catch {
    counts.value = { total: 0, inFlight: 0 };
  }
}

function label(s: string | null | undefined): string {
  return (s && REFUND_LABELS[s]) || statusLabel(s) || '-';
}
function tone(s: string | null | undefined): StatusTone {
  return (s && REFUND_TONES[s]) || statusTone(s);
}

function timeText(ms: number | string | null | undefined): string {
  const n = Number(ms ?? 0);
  if (n <= 0) return '—';
  const d = new Date(n);
  const p = (v: number): string => String(v).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

/**
 * 四步进度：提交申请 → 客服审核 → 退款处理 → 退款完成。
 * 用「当前走到第几步」的下标来表达，而不是逐状态写一堆布尔——
 * 被驳回（CLOSED）/ 退款失败（FAILED）属于中途终止：前两步算走过，但不点亮「进行中」光标。
 */
function stepsOf(r: RefundVO): { label: string; done: boolean; now: boolean }[] {
  const s = r.status ?? '';
  const STAGE: Record<string, number> = {
    PENDING: 1,
    REFUNDING: 2,
    REFUNDED: 4,
    CLOSED: 2,
    FAILED: 2,
  };
  const stage = STAGE[s] ?? 1;
  const terminated = s === 'CLOSED' || s === 'FAILED';
  return ['提交申请', '客服审核', '退款处理', '退款完成'].map((label, i) => ({
    label,
    done: i < stage,
    now: !terminated && stage < 4 && i === stage,
  }));
}

/** 只有被关闭 / 失败才给「重新申请」；已退款再申请没有意义，待审核重复申请会撞单 */
function canReapply(status: string | null | undefined): boolean {
  return status === 'CLOSED' || status === 'FAILED';
}

function goDetail(refundNo: string): void {
  router.push(`/refund/${refundNo}`);
}
function goOrder(orderNo: string): void {
  router.push(`/order/${orderNo}`);
}
function goProduct(r: RefundVO): void {
  const pid = Number(r.productId ?? 0);
  if (!pid) {
    goOrder(r.orderNo);
    return;
  }
  router.push(`/product/${pid}`);
}
function goChat(): void {
  router.push('/chat');
}
/** 重新申请：回到该订单的申请页（比列表全量刷新更直观） */
function reapply(orderNo: string): void {
  router.push(`/refund/apply/${orderNo}`);
}

async function select(value: string): Promise<void> {
  if (active.value === value) return;
  active.value = value;
  await fetchPage(1);
}

async function fetchPage(target: number): Promise<void> {
  loading.value = true;
  try {
    const query = active.value
      ? { page: target, size: PAGE_SIZE, status: active.value }
      : { page: target, size: PAGE_SIZE };
    const res = await unwrap(refundApi.list(query));
    const rows = res?.list ?? [];
    list.value = target === 1 ? rows : [...list.value, ...rows];
    total.value = Number(res?.total ?? 0);
    page.value = target;
  } catch (e) {
    if (target === 1) {
      list.value = [];
      total.value = 0;
    }
    ElMessage.error((e as Error).message || '加载失败');
  } finally {
    loading.value = false;
  }
}

async function loadMore(): Promise<void> {
  if (loading.value) return;
  await fetchPage(page.value + 1);
}

onMounted(() => {
  void Promise.all([fetchPage(1), loadCounts()]);
});
</script>

<style scoped>
.rf-page {
  width: var(--sent-container);
  max-width: calc(100% - 32px);
  margin: 0 auto;
  padding: 14px 0 36px;
}

/* ---------- 顶部条 ---------- */
.rf-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  background: linear-gradient(120deg, #127054 0%, #178a68 55%, #2aa87c 100%);
  border-radius: 12px;
  padding: 18px 22px;
  color: #fff;
}
.rf-head__title {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}
.rf-head__desc {
  margin: 6px 0 0;
  font-size: 12px;
  opacity: 0.86;
}
.rf-head__side {
  display: flex;
  align-items: center;
  gap: 14px;
  flex: none;
}
.rf-head__stat {
  text-align: center;
  background: rgba(255, 255, 255, 0.14);
  border-radius: 8px;
  padding: 6px 16px;
  min-width: 74px;
}
.rf-head__stat b {
  display: block;
  font-size: 20px;
  line-height: 1.2;
}
.rf-head__stat span {
  font-size: 11px;
  opacity: 0.85;
}
.rf-head__stat--hot b {
  color: #ffe0b8;
}
.rf-head__link {
  font-size: 13px;
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.45);
  border-radius: 14px;
  padding: 4px 12px;
}
.rf-head__link:hover {
  background: rgba(255, 255, 255, 0.16);
}

/* ---------- 状态筛选 ---------- */
.rf-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 14px 0 12px;
}
.rf-tab {
  border: 1px solid #fff;
  background: #fff;
  border-radius: 16px;
  padding: 6px 16px;
  font-size: 13px;
  color: var(--sent-text-2);
  cursor: pointer;
  transition: all 0.15s;
}
.rf-tab:hover {
  color: var(--sent-primary);
}
.rf-tab--on {
  color: var(--sent-primary);
  border-color: var(--sent-primary);
  font-weight: 700;
}

/* ---------- 卡片 ---------- */
.rf-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.rf-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px 18px 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  border: 1px solid transparent;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.rf-card:hover {
  border-color: var(--sent-primary);
  box-shadow: 0 6px 18px rgba(18, 102, 79, 0.08);
}

.rf-card__head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-bottom: 10px;
  border-bottom: 1px solid #f2f4f6;
  font-size: 12px;
  color: var(--sent-muted);
}
.rf-card__time {
  flex: none;
}
.rf-card__no {
  font-variant-numeric: tabular-nums;
}
.rf-card__status {
  margin-left: auto;
  font-size: 12px;
  border-radius: 10px;
  padding: 2px 10px;
  flex: none;
}
.rf-card__status--warning {
  color: #b26a00;
  background: #fff4e5;
}
.rf-card__status--success {
  color: #127054;
  background: #e8f5ef;
}
.rf-card__status--info {
  color: #6b7280;
  background: #f2f4f6;
}
.rf-card__status--danger {
  color: #c62828;
  background: #fdecec;
}

.rf-card__body {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 14px 0 12px;
}
.rf-card__goods {
  display: flex;
  gap: 12px;
  flex: 1;
  min-width: 0;
  cursor: pointer;
}
.rf-card__thumb {
  width: 74px;
  height: 74px;
  flex: none;
  border-radius: 8px;
  overflow: hidden;
}
.rf-card__thumb :deep(.thumb) {
  width: 74px;
  height: 74px;
}
.rf-card__info {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  justify-content: center;
}
.rf-card__name {
  font-size: 14px;
  color: var(--sent-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rf-card__goods:hover .rf-card__name {
  color: var(--sent-primary);
}
.rf-card__meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--sent-muted);
}
.rf-card__qty {
  flex: none;
}
.rf-card__reason {
  font-size: 12px;
  color: var(--sent-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rf-card__money {
  flex: none;
  text-align: right;
  min-width: 110px;
}
.rf-card__money-label {
  display: block;
  font-size: 11px;
  color: var(--sent-muted);
}
.rf-card__money b {
  font-size: 18px;
  color: var(--sent-accent-dark);
}

.rf-card__ops {
  flex: none;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  max-width: 260px;
}
.rf-btn {
  border: 1px solid var(--sent-border);
  background: #fff;
  border-radius: 14px;
  padding: 5px 14px;
  font-size: 12px;
  color: var(--sent-text-2);
  cursor: pointer;
  transition: all 0.15s;
  white-space: nowrap;
}
.rf-btn:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}
.rf-btn--main {
  border-color: var(--sent-accent);
  color: var(--sent-accent-dark);
  font-weight: 700;
}
.rf-btn--main:hover {
  background: var(--sent-accent);
  color: #fff;
}

/* ---------- 进度条 ---------- */
.rf-card__steps {
  display: flex;
  align-items: center;
  gap: 0;
  padding-top: 10px;
  border-top: 1px dashed #f0f2f4;
}
.rf-step {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #b9bfc4;
  flex: 1;
}
.rf-step__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #e3e6ea;
  flex: none;
}
.rf-step__line {
  flex: 1;
  height: 1px;
  background: #e9ecef;
  margin: 0 8px;
}
.rf-step--done {
  color: var(--sent-primary);
}
.rf-step--done .rf-step__dot {
  background: var(--sent-primary);
}
.rf-step--done .rf-step__line {
  background: var(--sent-primary);
}
.rf-step--now {
  font-weight: 700;
}
.rf-step--now .rf-step__dot {
  box-shadow: 0 0 0 3px rgba(23, 138, 104, 0.16);
}

/* ---------- 空态 ---------- */
.rf-hint {
  padding: 40px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}
.rf-empty {
  background: #fff;
  border-radius: 10px;
  padding: 56px 0;
  text-align: center;
  color: var(--sent-muted);
  font-size: 13px;
}
.rf-empty__icon {
  font-size: 38px;
  opacity: 0.35;
  margin-bottom: 10px;
}
.rf-empty__btn {
  display: inline-block;
  margin-top: 14px;
  background: var(--sent-accent);
  color: #fff;
  border-radius: 16px;
  padding: 7px 20px;
  font-size: 13px;
}
.rf-empty__btn:hover {
  background: var(--sent-accent-dark);
}

.rf-more {
  text-align: center;
  padding: 22px 0 0;
}
.rf-more__btn {
  border: 1px solid var(--sent-border);
  background: #fff;
  border-radius: 16px;
  padding: 7px 26px;
  font-size: 13px;
  color: var(--sent-text-2);
  cursor: pointer;
}
.rf-more__btn:hover {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}

@media (max-width: 1080px) {
  .rf-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .rf-card__body {
    flex-wrap: wrap;
  }
  .rf-card__ops {
    max-width: none;
    justify-content: flex-start;
  }
}
</style>
