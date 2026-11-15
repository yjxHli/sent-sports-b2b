<template>
  <div class="dashboard">
    <h2 class="dashboard-title">工作台</h2>
    <el-row :gutter="16">
      <el-col v-for="card in cards" :key="card.title" :xs="24" :sm="12" :md="8">
        <el-card shadow="hover" class="metric-card" v-loading="loading">
          <div class="metric-label">{{ card.title }}</div>
          <div class="metric-value">{{ card.value }}</div>
          <div class="metric-hint">{{ card.hint }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card header="角色化待办" class="todo-card">
      <div v-if="todos.length" class="todo-list">
        <div v-for="todo in todos" :key="todo.path" class="todo-item" @click="go(todo.path)">
          <span>{{ todo.text }} <span class="todo-count">{{ counts[todo.countKey] }} 项</span></span>
          <el-button link type="primary">前往处理</el-button>
        </div>
      </div>
      <el-empty v-else description="当前角色暂无待办" :image-size="70" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 工作台：六张指标卡 + 角色化待办数量，全部来自 GET /admin/reports/dashboard 真库聚合。
 * 不再有「等待 XX 接口」的占位卡，也不再把预警 SKU 的「可用量」误显示成「SKU 数」。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { fmtMoney } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { systemApi, type DashboardSummaryVO } from '@/api';
import { usePermissionStore } from '@/store/modules/permission';

interface MetricCard {
  title: string;
  value: string;
  hint: string;
}

type CountKey = keyof Pick<
  DashboardSummaryVO,
  'pendingShipCount' | 'pendingCustomReviewCount' | 'pendingChatCount'
  | 'warningSkuCount' | 'pendingRefundCount' | 'overdueReceivableCount'
>;

const router = useRouter();
const permission = usePermissionStore();
const loading = ref(false);

const cards = reactive<MetricCard[]>([
  { title: '今日订单', value: '--', hint: '今日成交订单' },
  { title: '今日销售额', value: '--', hint: '今日实付金额' },
  { title: '待发货', value: '--', hint: '待发货 / 部分发货' },
  { title: '待审核定制', value: '--', hint: 'WAIT_REVIEW 待审图稿' },
  { title: '库存预警 SKU', value: '--', hint: '可售低于安全库存' },
  { title: '待接入会话', value: '--', hint: 'WAITING 排队会话' },
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
  { perm: 'chat:serve', text: '待接入客服会话', path: '/chat/workbench', countKey: 'pendingChatCount' },
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
    cards[0].value = String(data.todayOrderCount);
    cards[0].hint = `${data.date} 成交 ${data.todayOrderCount} 笔`;
    cards[1].value = fmtMoney(data.todayAmountCent);
    cards[1].hint = `${data.date} 实付金额`;
    cards[2].value = String(data.pendingShipCount);
    cards[3].value = String(data.pendingCustomReviewCount);
    cards[4].value = String(data.warningSkuCount);
    cards[5].value = String(data.pendingChatCount);
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
.dashboard-title { margin: 0 0 18px; font-size: 18px; color: #1f2d28; }
.metric-card { margin-bottom: 16px; }
.metric-label { font-size: 13px; color: #7b8a84; }
.metric-value { margin: 10px 0 6px; font-size: 28px; font-weight: 700; color: #12664f; }
.metric-hint { font-size: 12px; color: #a4b0ab; }
.todo-card { margin-top: 4px; }
.todo-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 4px;
  border-bottom: 1px solid #eef2f0;
  cursor: pointer;
  font-size: 14px;
}
.todo-item:last-child { border-bottom: none; }
.todo-count { margin-left: 8px; font-size: 12px; color: #12664f; }
</style>
