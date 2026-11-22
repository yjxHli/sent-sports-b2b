<template>
  <div class="dashboard">
    <div class="sent-page-head">
      <div class="sent-page-head__main">
        <h2 class="sent-page-title">工作台</h2>
        <p class="sent-page-desc">订单、库存、定制与客服的今日汇总，数字由后端按当日报表聚合。</p>
      </div>
      <div class="sent-page-actions">
        <span class="sent-page-meta sent-num">{{ today }}</span>
      </div>
    </div>

    <!--
      指标卡：label / 数字 + 单位 / 说明，右上角一个语义图标。
      只有「库存预警」这种要人动手的数字用强调橙，其余一律墨色 —— 一屏只允许一个数字抢眼。
    -->
    <div v-loading="loading" class="sent-stat-row dashboard-stats">
      <div
        v-for="card in cards"
        :key="card.title"
        class="sent-stat"
        :class="{ 'sent-stat--alert': card.alert }"
      >
        <span class="sent-stat__label">{{ card.title }}</span>
        <el-icon class="sent-stat__icon"><component :is="card.icon" /></el-icon>
        <strong class="sent-stat__value metric-value sent-num">
          {{ card.value }}<span v-if="card.unit" class="sent-stat__unit">{{ card.unit }}</span>
        </strong>
        <span class="sent-stat__hint metric-hint">{{ card.hint }}</span>
      </div>
    </div>

    <div class="sent-panel todo-panel">
      <div class="sent-panel__head">
        <h3 class="section-title">角色化待办</h3>
        <span class="sent-panel__meta">{{ todos.length }} 项</span>
      </div>
      <div v-if="todos.length" class="todo-list">
        <div v-for="todo in todos" :key="todo.path" class="todo-item" @click="go(todo.path)">
          <span class="todo-item__text">
            {{ todo.text }}
            <span class="todo-count sent-num">{{ counts[todo.countKey] }}</span>
            <span class="todo-unit">项</span>
          </span>
          <el-button link class="todo-go">前往处理</el-button>
        </div>
      </div>
      <el-empty v-else description="当前角色暂无待办" :image-size="70" />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 工作台：六张指标卡 + 角色化待办数量，全部来自 GET /admin/reports/dashboard 真库聚合。
 * 不再有「等待 XX 接口」的占位卡，也不再把预警 SKU 的「可用量」误显示成「SKU 数」。
 */
import { computed, markRaw, onMounted, reactive, ref, type Component } from 'vue';
import { useRouter } from 'vue-router';
import {
  ChatDotRound,
  Coin,
  Picture,
  Tickets,
  Van,
  Warning,
} from '@element-plus/icons-vue';
import { fmtMoney } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { systemApi, type DashboardSummaryVO } from '@/api';
import { usePermissionStore } from '@/store/modules/permission';

interface MetricCard {
  title: string;
  value: string;
  hint: string;
  /** 数字后面的单位，销售额自带 ¥ 不需要 */
  unit?: string;
  icon: Component;
  /** 需要人动手的数字才标强调色 */
  alert?: boolean;
}

type CountKey = keyof Pick<
  DashboardSummaryVO,
  'pendingShipCount' | 'pendingCustomReviewCount' | 'pendingChatCount'
  | 'warningSkuCount' | 'pendingRefundCount' | 'overdueReceivableCount'
>;

const router = useRouter();
const permission = usePermissionStore();
const loading = ref(false);
const today = ref('');

const cards = reactive<MetricCard[]>([
  { title: '今日订单', value: '--', hint: '今日成交订单', unit: '笔', icon: markRaw(Tickets) },
  { title: '今日销售额', value: '--', hint: '今日实付金额', icon: markRaw(Coin) },
  { title: '待发货', value: '--', hint: '待发货 / 部分发货', unit: '笔', icon: markRaw(Van) },
  { title: '待审核定制', value: '--', hint: 'WAIT_REVIEW 待审图稿', unit: '项', icon: markRaw(Picture) },
  { title: '库存预警 SKU', value: '--', hint: '可售低于安全库存', unit: '个', icon: markRaw(Warning) },
  { title: '待接入会话', value: '--', hint: 'WAITING 排队会话', unit: '个', icon: markRaw(ChatDotRound) },
]);

const counts = reactive<Record<CountKey, number>>({
  pendingShipCount: 0,
  pendingCustomReviewCount: 0,
  pendingChatCount: 0,
  warningSkuCount: 0,
  pendingRefundCount: 0,
  overdueReceivableCount: 0,
});

const TODO_DEFS: Array<{ perm: string; text: string; path: string; countKey: CountKey }> = [
  { perm: 'order:ship', text: '待发货订单', path: '/order/shipment', countKey: 'pendingShipCount' },
  { perm: 'custom:review', text: '待审核定制图稿', path: '/custom/review', countKey: 'pendingCustomReviewCount' },
  { perm: 'refund:audit', text: '待审核退款申请', path: '/finance/refund', countKey: 'pendingRefundCount' },
  { perm: 'finance:read', text: '逾期应收跟进', path: '/finance/receivable', countKey: 'overdueReceivableCount' },
  { perm: 'stock:read', text: '库存预警处理', path: '/report/stock-warning', countKey: 'warningSkuCount' },
  // 「待接入客服会话」待办已下线：客服工作台剥离到独立工程 web-cs（:5175），
  // 管理端不再承担接待入口；排队量仍在下方「待接入会话」指标卡里展示（只读监控）。
];
const todos = computed(() => TODO_DEFS.filter((item) => permission.has(item.perm)));

function go(path: string): void {
  void router.push(path);
}

onMounted(async () => {
  loading.value = true;
  const [summary, warnings] = await Promise.allSettled([
    unwrap(systemApi.dashboard()),
    unwrap(systemApi.stockWarningReport()),
  ]);
  const data = summary.status === 'fulfilled' ? summary.value : null;
  if (data) {
    today.value = data.date;
    cards[0].value = String(data.todayOrderCount);
    cards[0].hint = `${data.date} 成交 ${data.todayOrderCount} 笔`;
    cards[1].value = fmtMoney(data.todayAmountCent);
    cards[1].hint = `${data.date} 实付金额`;
    cards[2].value = String(data.pendingShipCount);
    cards[3].value = String(data.pendingCustomReviewCount);
    cards[4].value = String(data.warningSkuCount);
    cards[5].value = String(data.pendingChatCount);
    cards[4].alert = Number(data.warningSkuCount) > 0;
    counts.pendingShipCount = Number(data.pendingShipCount);
    counts.pendingCustomReviewCount = Number(data.pendingCustomReviewCount);
    counts.pendingChatCount = Number(data.pendingChatCount);
    counts.warningSkuCount = Number(data.warningSkuCount);
    counts.pendingRefundCount = Number(data.pendingRefundCount);
    counts.overdueReceivableCount = Number(data.overdueReceivableCount);
  }
  if (warnings.status === 'fulfilled' && warnings.value) {
    const w = warnings.value;
    cards[4].hint = w.skuName
      ? `最告急：${w.skuName} · 阈值 ${w.threshold}`
      : '可售低于安全库存';
  }
  loading.value = false;
});
</script>

<style scoped>
.sent-page-meta {
  font-size: 13px;
  color: var(--sent-ink-3);
}

.dashboard-stats {
  margin-bottom: var(--sp-4);
}

/* ---------- 待办 ---------- */
.todo-panel {
  padding-bottom: var(--sp-3);
}
.todo-list {
  display: flex;
  flex-direction: column;
}
.todo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 44px;
  border-bottom: 1px solid var(--sent-line);
  cursor: pointer;
  font-size: 13px;
  color: var(--sent-ink);
  transition: color var(--sent-ease);
}
.todo-item:last-child {
  border-bottom: none;
}
.todo-item:hover {
  color: var(--sent-primary);
}
.todo-item__text {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.todo-count {
  font-size: 13px;
  font-weight: 500;
  color: var(--sent-ink);
}
.todo-unit {
  font-size: 13px;
  color: var(--sent-ink-3);
}
/* 「前往处理」重复五次是噪音：行本身可点，按钮只在悬停时出现 */
.todo-go {
  font-size: 13px;
  color: var(--sent-ink-3);
  opacity: 0;
  transition: opacity var(--sent-ease);
}
.todo-item:hover .todo-go {
  opacity: 1;
  color: var(--sent-primary);
}

@media (max-width: 1280px) {
  .dashboard-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .dashboard-stats {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
