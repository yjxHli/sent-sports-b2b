<template>
  <div class="container my-custom">
    <!-- 品牌化页头：保留 .sent-bar，首页「定制队服」入口验收依赖它包含「我的定制」 -->
    <div class="sent-bar custom-hero">
      <div class="custom-hero__text">
        <span class="custom-hero__eyebrow">MY CUSTOM ORDERS</span>
        <strong class="custom-hero__title">我的定制</strong>
        <span class="custom-hero__sub">从图稿到发货，全程追踪每一件专属队服</span>
      </div>
      <svg class="custom-hero__art" viewBox="0 0 120 120" fill="none" aria-hidden="true">
        <circle cx="60" cy="60" r="55" fill="rgba(255,255,255,.07)" stroke="rgba(255,255,255,.18)" stroke-width="1.5" />
        <circle cx="60" cy="60" r="42" fill="rgba(255,255,255,.06)" />
        <path
          d="M46 35 34 41 27 58l11 5 2-6v40h40V57l2 6 11-5-7-17-12-6c-3 6-7 9-14 9s-11-3-14-9Z"
          fill="rgba(255,255,255,.92)"
        />
        <path d="M51 60h18M51 69h18" stroke="#12664f" stroke-width="2.5" stroke-linecap="round" />
      </svg>
    </div>

    <!-- 状态概览：当前页统计，纯展示，不改变查询口径 -->
    <div class="custom-stats">
      <div class="stat-card stat-card--todo">
        <div class="stat-card__top"><span class="stat-card__label">待我处理</span><i class="stat-card__dot" /></div>
        <div class="stat-card__num">{{ stats.todo }}</div>
        <div class="stat-card__hint">提交图稿 / 修改图稿</div>
      </div>
      <div class="stat-card stat-card--review">
        <div class="stat-card__top"><span class="stat-card__label">审核中</span><i class="stat-card__dot" /></div>
        <div class="stat-card__num">{{ stats.review }}</div>
        <div class="stat-card__hint">等待审图结果</div>
      </div>
      <div class="stat-card stat-card--producing">
        <div class="stat-card__top"><span class="stat-card__label">制作中</span><i class="stat-card__dot" /></div>
        <div class="stat-card__num">{{ stats.producing }}</div>
        <div class="stat-card__hint">已通过 / 生产中</div>
      </div>
      <div class="stat-card stat-card--done">
        <div class="stat-card__top"><span class="stat-card__label">已完成</span><i class="stat-card__dot" /></div>
        <div class="stat-card__num">{{ stats.done }}</div>
        <div class="stat-card__hint">已完工 / 已发货</div>
      </div>
    </div>

    <!-- 单号查询 -->
    <div class="sent-card custom-query">
      <div class="custom-query__head">
        <span class="custom-query__title">按定制单号查询</span>
        <span class="custom-query__hint">输入单号直达图稿与审核记录</span>
      </div>
      <el-input v-model="orderId" placeholder="输入定制单号直接查询，如 CU20261115001" @keyup.enter="query">
        <template #prefix>
          <span class="search-icon">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <circle cx="7" cy="7" r="4.5" stroke="currentColor" stroke-width="1.5" />
              <path d="m10.5 10.5 3.5 3.5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
            </svg>
          </span>
        </template>
        <template #append>
          <el-button type="primary" @click="query">查询</el-button>
        </template>
      </el-input>
    </div>

    <!-- 定制单列表 -->
    <section class="custom-section">
      <div class="custom-section__head">
        <div class="custom-section__title">
          <strong>定制单列表</strong>
          <span class="custom-section__count">本页 {{ rows.length }} / 共 {{ total }} 单</span>
        </div>
        <el-button link type="primary" :loading="loading" @click="load">刷新</el-button>
      </div>

      <div v-if="loading && rows.length === 0" class="custom-skeleton">
        <div v-for="n in 3" :key="n" class="skeleton-row" />
      </div>
      <el-empty
        v-else-if="!loading && rows.length === 0"
        class="custom-empty"
        description="暂无定制单，可在首页选择「定制队服」开启定制"
        :image-size="84"
      />
      <div v-else class="custom-list">
        <div
          v-for="row in rows"
          :key="row.customOrderId"
          class="custom-row"
          role="link"
          tabindex="0"
          @click="openDetail(row.customOrderId)"
          @keyup.enter="openDetail(row.customOrderId)"
        >
          <div class="row-main">
            <div class="row-main__left">
              <span class="row-icon">
                <svg width="19" height="19" viewBox="0 0 20 20" fill="none" aria-hidden="true">
                  <path
                    d="m7.5 3-3 1.5L2.5 8l2.2 1 .6-1.4V17h11.4V7.6l.6 1.4 2.2-1-2-3.5L12.5 3c-.7 1.4-1.7 2-2.5 2s-1.8-.6-2.5-2Z"
                    stroke="currentColor"
                    stroke-width="1.4"
                    stroke-linejoin="round"
                  />
                </svg>
              </span>
              <span class="row-identity">
                <strong>{{ row.customOrderId }}</strong>
                <span class="row-order">关联订单状态 · {{ row.orderStatus || '--' }}</span>
              </span>
            </div>
            <span class="row-right">
              <el-tag :type="tone(row.status)" size="small" effect="dark">{{ label(row.status) }}</el-tag>
              <span class="row-go">
                查看详情
                <svg width="13" height="13" viewBox="0 0 14 14" fill="none" aria-hidden="true">
                  <path d="m5 3 4 4-4 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </span>
            </span>
          </div>

          <div class="row-flow" aria-hidden="true">
            <span
              v-for="(step, index) in FLOW_STEPS"
              :key="step"
              class="flow-step"
              :class="`flow-step--${flowState(row.status, index)}`"
            >
              <i class="flow-step__dot" />
              <span class="flow-step__label">{{ step }}</span>
              <i v-if="index < FLOW_STEPS.length - 1" class="flow-step__line" />
            </span>
          </div>

          <div
            v-if="needsAction(row.status)"
            class="row-alert"
            :class="{ 'row-alert--reject': row.status === 'REJECTED' }"
          >
            <svg width="13" height="13" viewBox="0 0 14 14" fill="none" aria-hidden="true">
              <circle cx="7" cy="7" r="5.6" stroke="currentColor" stroke-width="1.3" />
              <path d="M7 4v3.3l2.1 1.4" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" />
            </svg>
            {{ actionHint(row.status) }}
          </div>

          <div class="row-sub">
            <span class="row-meta">
              <svg width="13" height="13" viewBox="0 0 14 14" fill="none" aria-hidden="true">
                <path d="M3 1.8h5l3 3V12a.5.5 0 0 1-.5.5h-7A.5.5 0 0 1 3 12V2.3a.5.5 0 0 1 0-.5Z" stroke="currentColor" stroke-width="1.2" />
                <path d="M8 1.8v3h3" stroke="currentColor" stroke-width="1.2" />
              </svg>
              图稿 v{{ row.version }}
            </span>
            <span class="row-meta">
              <svg width="13" height="13" viewBox="0 0 14 14" fill="none" aria-hidden="true">
                <path d="M2.5 7a4.5 4.5 0 1 0 1.4-3.2M2.5 2.5v2.6h2.6" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
              驳回 {{ row.rejectCount }} 次
            </span>
            <span v-if="Number(row.reviewDeadlineMillis) > 0" class="row-meta">
              <svg width="13" height="13" viewBox="0 0 14 14" fill="none" aria-hidden="true">
                <rect x="2" y="3" width="10" height="9" rx="1.4" stroke="currentColor" stroke-width="1.2" />
                <path d="M2 5.6h10M4.7 1.8v2.2M9.3 1.8v2.2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
              </svg>
              审核截止 {{ fmtDateTime(Number(row.reviewDeadlineMillis)) }}
            </span>
          </div>
        </div>
      </div>

      <el-pagination
        v-if="total > 0"
        class="list-page"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-size="size"
        :page-sizes="[10, 15, 20, 50, 100]"
        :current-page="page"
        @current-change="onPage"
        @size-change="onSize"
      />
    </section>

    <p class="custom-tip">列表与详情均来自后端真实查询；详情页可查看每一版图稿、审核记录并重新上传。</p>
  </div>
</template>

<script setup lang="ts">
/**
 * 我的定制（列表）：每一行点进去到独立详情页 `/custom/:customOrderId`。
 * 详情（图稿版本 / 审核记录 / 重新上传）都在详情页做，列表只负责定位与状态概览。
 */
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { customApi, type MyCustomOrderVO } from '@/api/custom';
import { fmtDateTime, statusLabel, statusTone, type StatusTone } from '@sent/shared';

const router = useRouter();

const orderId = ref('');
const rows = ref<MyCustomOrderVO[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(false);

const FLOW_STEPS = ['提交图稿', '审核', '生产', '发货'];

/** 状态 → 流程节点（0~4，4 表示全程完成；-1 表示终止类状态不展示进度） */
function flowIndex(status: string): number {
  switch (status) {
    case 'WAIT_DRAFT':
      return 0;
    case 'WAIT_REVIEW':
    case 'REJECTED':
    case 'ESCALATED':
      return 1;
    case 'APPROVED':
    case 'IN_PRODUCTION':
      return 2;
    case 'FINISHED':
      return 3;
    case 'SHIPPED':
      return 4;
    default:
      return -1;
  }
}

type FlowState = 'done' | 'active' | 'blocked' | 'idle';
function flowState(status: string, index: number): FlowState {
  const current = flowIndex(status);
  if (current < 0) return 'idle';
  if (index < current) return 'done';
  if (index === current) return status === 'REJECTED' ? 'blocked' : 'active';
  return 'idle';
}

/** 需要客户本人补充动作的状态 */
function needsAction(status: string): boolean {
  return status === 'WAIT_DRAFT' || status === 'REJECTED';
}
function actionHint(status: string): string {
  return status === 'REJECTED'
    ? '图稿未通过审核，请根据审核意见重新上传'
    : '图稿还未提交，上传后即可进入审核流程';
}

/** 当前页状态概览（纯展示统计，不改变查询口径） */
const stats = computed(() => {
  const list = rows.value;
  return {
    todo: list.filter((r) => r.status === 'WAIT_DRAFT' || r.status === 'REJECTED').length,
    review: list.filter((r) => r.status === 'WAIT_REVIEW' || r.status === 'ESCALATED').length,
    producing: list.filter((r) => r.status === 'APPROVED' || r.status === 'IN_PRODUCTION').length,
    done: list.filter((r) => r.status === 'FINISHED' || r.status === 'SHIPPED').length,
  };
});

function label(s: string): string {
  return statusLabel(s);
}
function tone(s: string): StatusTone {
  return statusTone(s);
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await unwrap(customApi.my({ page: page.value, size: size.value }));
    rows.value = data?.list ?? [];
    total.value = Number(data?.total ?? 0);
  } catch {
    rows.value = [];
    total.value = 0;
    ElMessage.error('定制单列表加载失败');
  } finally {
    loading.value = false;
  }
}

/** 点一行 → 详情页 */
function openDetail(customOrderId: string): void {
  router.push(`/custom/${customOrderId}`);
}

/** 手输单号：合法就直接进详情页，非法给提示（不再原地展开） */
function query(): void {
  const id = orderId.value.trim();
  if (!id) {
    ElMessage.warning('请输入定制单号');
    return;
  }
  openDetail(id);
}

function onPage(p: number): void {
  page.value = p;
  void load();
}
function onSize(s: number): void {
  size.value = s;
  page.value = 1;
  void load();
}

onMounted(() => {
  void load();
});
</script>

<style scoped>
.my-custom {
  padding-top: 16px;
  padding-bottom: 24px;
}

/* ============ 品牌页头 ============ */
.custom-hero.sent-bar {
  position: relative;
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 132px;
  padding: 24px 32px;
  margin-bottom: 16px;
  border: 0;
  border-radius: 12px;
  background: linear-gradient(115deg, #0d4d3b 0%, #12664f 45%, #1c8a6b 100%);
  box-shadow: 0 6px 20px rgba(18, 102, 79, 0.16);
}
.custom-hero::before {
  content: '';
  position: absolute;
  right: -60px;
  top: -90px;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.05);
}
.custom-hero__text {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.custom-hero__eyebrow {
  font-size: 10px;
  letter-spacing: 0.22em;
  color: rgba(255, 255, 255, 0.62);
  font-weight: 600;
}
.custom-hero__title {
  font-size: 26px;
  line-height: 1.25;
  color: #fff;
  font-weight: 700;
  letter-spacing: 0.02em;
}
.custom-hero__sub {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.78);
}
.custom-hero__art {
  position: relative;
  z-index: 1;
  width: 104px;
  height: 104px;
  flex: none;
}

/* ============ 状态概览 ============ */
.custom-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  position: relative;
  overflow: hidden;
  background: var(--sent-card);
  border: 1px solid var(--sent-border);
  border-radius: var(--sent-radius);
  padding: 14px 18px;
}
.stat-card::after {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  background: var(--stat-color);
}
.stat-card--todo {
  --stat-color: #ff7a1a;
}
.stat-card--review {
  --stat-color: #3b82f6;
}
.stat-card--producing {
  --stat-color: #8b5cf6;
}
.stat-card--done {
  --stat-color: var(--sent-primary);
}
.stat-card__top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stat-card__label {
  font-size: 13px;
  color: var(--sent-text-2);
  font-weight: 600;
}
.stat-card__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--stat-color);
  opacity: 0.8;
}
.stat-card__num {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
  margin-top: 6px;
  color: var(--sent-text);
  font-variant-numeric: tabular-nums;
}
.stat-card__hint {
  font-size: 11px;
  color: var(--sent-muted);
  margin-top: 2px;
}

/* ============ 单号查询 ============ */
.custom-query {
  padding: 16px 18px 18px;
}
.custom-query__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}
.custom-query__title {
  font-size: 14px;
  font-weight: 600;
}
.custom-query__hint {
  font-size: 12px;
  color: var(--sent-muted);
}
.custom-query .el-input {
  max-width: 560px;
}
.search-icon {
  display: flex;
  align-items: center;
  color: var(--sent-muted);
}

/* ============ 列表容器 ============ */
.custom-section {
  background: var(--sent-card);
  border: 1px solid var(--sent-border);
  border-radius: var(--sent-radius);
  padding: 0 18px 18px;
}
.custom-section__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px solid var(--sent-border);
}
.custom-section__title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
}
.custom-section__count {
  font-size: 11px;
  font-weight: 500;
  color: var(--sent-primary);
  background: var(--sent-primary-soft);
  padding: 2px 8px;
  border-radius: 10px;
}

/* ============ 定制单卡片 ============ */
.custom-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}
.custom-row {
  border: 1px solid #e8eaed;
  border-radius: 8px;
  padding: 14px 16px 13px;
  cursor: pointer;
  background: var(--sent-card);
  transition: border-color 0.18s, box-shadow 0.18s, transform 0.18s;
}
.custom-row:hover {
  border-color: var(--sent-primary);
  box-shadow: 0 4px 14px rgba(18, 102, 79, 0.1);
  transform: translateY(-1px);
}
.custom-row:focus-visible {
  outline: 2px solid var(--sent-primary);
  outline-offset: 2px;
}
.row-main {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}
.row-main__left {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}
.row-icon {
  width: 34px;
  height: 34px;
  flex: none;
  border-radius: 8px;
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
  display: flex;
  align-items: center;
  justify-content: center;
}
.row-identity {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.row-identity strong {
  font-size: 14px;
  color: var(--sent-text);
  font-weight: 700;
  letter-spacing: 0.01em;
  font-variant-numeric: tabular-nums;
}
.row-order {
  font-size: 11px;
  color: var(--sent-muted);
}
.row-right {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  flex: none;
}
.row-go {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  color: var(--sent-muted);
  white-space: nowrap;
}
.custom-row:hover .row-go {
  color: var(--sent-primary);
}

/* ============ 流程进度（分段式状态轨道，文字与线条分离不重叠） ============ */
.row-flow {
  display: flex;
  align-items: center;
  margin: 16px 0 13px 45px;
}
.flow-step {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: none;
}
/* 末位节点占满剩余宽度，让前几段的连接线伸展填充 */
.flow-step:not(:last-child) {
  flex: 1;
}
/* 节点：12px 彩色实心 + 3px 白描边 */
.flow-step__dot {
  width: 12px;
  height: 12px;
  box-sizing: content-box;
  border: 3px solid #fff;
  border-radius: 50%;
  background: #d3dae1;
  flex: none;
  transition: background 0.2s, box-shadow 0.2s;
}
/* 标签：紧跟圆点，独立于线条，避免文字被线穿过 */
.flow-step__label {
  font-size: 11px;
  margin: 0 10px 0 7px;
  color: #a2aab5;
  white-space: nowrap;
  flex: none;
}
/* 连接线：独立元素，从标签右侧延伸到下一节点，与文字零重叠 */
.flow-step__line {
  height: 3px;
  border-radius: 2px;
  background: #e5eaef;
  flex: 1;
  min-width: 16px;
  transition: background 0.2s;
}

/* 已完成：主题绿节点 + 绿色轨道 */
.flow-step--done .flow-step__dot {
  background: var(--sent-primary);
}
.flow-step--done .flow-step__line {
  background: linear-gradient(90deg, var(--sent-primary), #2f9e79);
}
.flow-step--done .flow-step__label {
  color: var(--sent-text-2);
}

/* 当前节点：橙色 + 柔光环 + 橙色轨道（表示正在推进到下一步） */
.flow-step--active .flow-step__dot {
  background: var(--sent-accent);
  box-shadow: 0 0 0 4px var(--sent-accent-soft);
}
.flow-step--active .flow-step__line {
  background: linear-gradient(90deg, var(--sent-accent), #ffb477);
}
.flow-step--active .flow-step__label {
  color: var(--sent-accent-dark);
  font-weight: 600;
}

/* 驳回：红色 + 红色柔光环 */
.flow-step--blocked .flow-step__dot {
  background: #e34d59;
  box-shadow: 0 0 0 4px #fdeaec;
}
.flow-step--blocked .flow-step__label {
  color: #e34d59;
  font-weight: 600;
}

/* ============ 行动提示 ============ */
.row-alert {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 10px 45px;
  padding: 7px 10px;
  border-radius: 5px;
  font-size: 11px;
  background: var(--sent-accent-soft);
  color: var(--sent-accent-dark);
}
.row-alert--reject {
  background: #fdeaec;
  color: #c93644;
}

/* ============ 元信息 ============ */
.row-sub {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-left: 45px;
  font-size: 11px;
  color: var(--sent-muted);
}
.row-meta {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

/* ============ 空态 / 骨架 / 分页 ============ */
.custom-empty {
  padding: 26px 0 10px;
}
.custom-skeleton {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}
.skeleton-row {
  height: 92px;
  border-radius: 8px;
  background: linear-gradient(90deg, #f6f7f8 25%, #eef0f2 50%, #f6f7f8 75%);
  background-size: 200% 100%;
  animation: sent-shimmer 1.2s infinite;
}
@keyframes sent-shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
.custom-tip {
  font-size: 11px;
  color: #aab0b9;
  padding: 12px 4px 0;
  line-height: 1.6;
}

@media (max-width: 900px) {
  .custom-stats {
    grid-template-columns: repeat(2, 1fr);
  }
  .custom-hero__art {
    display: none;
  }
  .row-flow,
  .row-sub,
  .row-alert {
    margin-left: 0;
  }
}
</style>
