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
          <span>{{ todo.text }}</span>
          <el-button link type="primary">前往处理</el-button>
        </div>
      </div>
      <el-empty v-else description="当前角色暂无待办" :image-size="70" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
/** 工作台（P6-24）：六个指标卡接报表，待办按 permissions[] 角色化过滤 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { fmtMoney } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { systemApi } from '@/api';
import { usePermissionStore } from '@/store/modules/permission';

interface MetricCard {
  title: string;
  value: string;
  hint: string;
}

const router = useRouter();
const permission = usePermissionStore();
const loading = ref(false);

const cards = reactive<MetricCard[]>([
  { title: '今日订单', value: '--', hint: '等待销售报表接口' },
  { title: '今日销售额', value: '--', hint: '等待销售报表接口' },
  { title: '待发货', value: '--', hint: '等待订单列表接口' },
  { title: '待审核定制', value: '--', hint: '等待定制审核接口' },
  { title: '库存预警 SKU', value: '--', hint: '等待库存预警接口' },
  { title: '待接入会话', value: '--', hint: '等待客服会话接口' },
]);

const TODO_DEFS = [
  { perm: 'order:ship', text: '待发货订单', path: '/order/shipment' },
  { perm: 'custom:review', text: '待审核定制图稿', path: '/custom/review' },
  { perm: 'refund:audit', text: '待审核退款申请', path: '/finance/refund' },
  { perm: 'finance:read', text: '逾期应收跟进', path: '/finance/receivable' },
  { perm: 'stock:read', text: '库存预警处理', path: '/report/stock-warning' },
  { perm: 'chat:serve', text: '待接入客服会话', path: '/chat/workbench' },
];
const todos = computed(() => TODO_DEFS.filter((item) => permission.has(item.perm)));

function go(path: string): void {
  void router.push(path);
}

onMounted(async () => {
  loading.value = true;
  try {
    const [sales, warnings] = await Promise.all([
      unwrap(systemApi.salesReport()),
      unwrap(systemApi.stockWarningReport()),
    ]);
    cards[0].value = String(sales.orderCount);
    cards[0].hint = `${sales.date} 订单数`;
    cards[1].value = fmtMoney(sales.amountCent);
    cards[1].hint = `${sales.date} 销售额`;
    cards[4].value = String(warnings.availableQty);
    cards[4].hint = `${warnings.skuName || '预警 SKU'} · 阈值 ${warnings.threshold}`;
  } catch {
    // 后端未启动或报表接口未就绪时保留 '--'，不阻塞页面与菜单
  } finally {
    loading.value = false;
  }
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
</style>
