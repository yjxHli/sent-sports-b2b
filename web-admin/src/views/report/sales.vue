<template>
  <div>
    <h2 class="sent-page-title">销售报表</h2>

    <div class="trend-toolbar">
      <span class="trend-toolbar__label">统计区间</span>
      <el-radio-group :model-value="days" size="small" @change="selectDays">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
        <el-radio-button :value="90">近 90 天</el-radio-button>
      </el-radio-group>
      <span v-if="trend" class="trend-toolbar__range">{{ trend.fromDate }} ~ {{ trend.toDate }}</span>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="8">
        <div class="sent-panel metric">
          <span class="metric-label">统计日期</span>
          <strong class="metric-value">{{ trend ? `${trend.days} 天` : '--' }}</strong>
          <span class="metric-sub">以支付时间 paid_at 为基准</span>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8">
        <div class="sent-panel metric">
          <span class="metric-label">销售额（实付）</span>
          <strong class="metric-value">{{ trend ? fmtMoney(trend.totalAmountCent) : '--' }}</strong>
          <span class="metric-sub">SUM(paid_cent)，剔除全额退款</span>
        </div>
      </el-col>
      <el-col :xs="24" :sm="8">
        <div class="sent-panel metric">
          <span class="metric-label">成交订单</span>
          <strong class="metric-value">{{ trend?.totalOrderCount ?? '--' }}</strong>
          <span class="metric-sub">客单价 {{ trend?.avgOrderYuan ? `¥${trend.avgOrderYuan}` : '--' }}</span>
        </div>
      </el-col>
    </el-row>

    <div class="sent-panel">
      <h3 class="section-title">销售趋势（按日）</h3>
      <div v-if="trend && trend.points.length" class="trend-chart">
        <div
          v-for="(p, i) in trend.points"
          :key="p.date"
          class="trend-col"
          :title="`${p.date}　销售额 ¥${p.amountYuan}　订单 ${p.orderCount} 笔`"
        >
          <div class="trend-col__bar" :style="{ height: barHeight(p.amountCent) }" />
          <span v-if="showLabel(i)" class="trend-col__label">{{ p.date.slice(5) }}</span>
        </div>
      </div>
      <el-empty v-else-if="!loading" description="暂无趋势数据" :image-size="60" />
      <p class="sent-hint">
        口径：以 orders.paid_at 为时间基准，按 SUM(paid_cent) 聚合；仅统计 PAID / PART_REFUNDED，
        全额退款（FULL_REFUNDED）订单整单剔除，不计入销售业绩。无成交日期补零。
      </p>
    </div>

    <div class="sent-panel">
      <h3 class="section-title">销售业绩排行</h3>
      <SentTable
        :columns="columns"
        :rows="pagedRanks"
        :total="ranks.length"
        :page="page"
        :size="size"
        :loading="loading"
        exportable
        export-name="销售业绩排行"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #col-amountCent="{ row }">
          <div class="bar-cell">
            <div class="bar" :style="{ width: barWidth(row.amountCent) }" />
            <span>{{ fmtMoney(row.amountCent) }}</span>
          </div>
        </template>
      </SentTable>
      <p class="sent-hint">
        后端 P4 排行报表仍为单条快照，此处不伪造多行排行；销售趋势数据已接真库。
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
/** 销售报表（批 4）：近 N 天销售趋势真查 + 汇总卡 + 业绩排行，条形长度按最大值归一。 */
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { systemApi, type SalesRankVO, type SalesTrendVO } from '@/api';
import { fmtMoney } from '@sent/shared';
import { usePagination } from '@/composables/usePagination';
import SentTable from '@/components/SentTable.vue';
import type { TableColumn } from '@/components/types';

const columns: TableColumn[] = [
  { prop: 'rank', label: '名次', width: 90, align: 'center' },
  { prop: 'name', label: '销售 / 团队', minWidth: 180 },
  { prop: 'amountCent', label: '销售额', minWidth: 260 },
];

const days = ref(30);
const trend = ref<SalesTrendVO | null>(null);
const ranks = ref<SalesRankVO[]>([]);
const loading = ref(false);
const { page, size } = usePagination();
/** 排行数据一次性返回，展示层按当前页切片；后端补多行排行后自动生效。 */
const pagedRanks = computed(() =>
  ranks.value.slice((page.value - 1) * size.value, page.value * size.value),
);

function onPage(value: number): void {
  page.value = value;
}
function onSize(value: number): void {
  size.value = value;
  page.value = 1;
}

const maxTrendAmount = computed(() =>
  Math.max(1, ...(trend.value?.points ?? []).map((p) => Number(p.amountCent ?? 0))),
);
const maxAmount = computed(() =>
  Math.max(1, ...ranks.value.map((item) => Number(item.amountCent ?? 0))),
);

function barHeight(amountCent: number | string): string {
  const ratio = Number(amountCent ?? 0) / maxTrendAmount.value;
  return `${Math.max(3, Math.round(ratio * 100))}%`;
}
function barWidth(amountCent: number): string {
  const ratio = Number(amountCent ?? 0) / maxAmount.value;
  return `${Math.max(2, Math.round(ratio * 100))}%`;
}
/** 日期标签按间隔抽样，避免 30/90 根柱子下方文字挤成一团。 */
function showLabel(i: number): boolean {
  const step = days.value >= 90 ? 15 : days.value >= 30 ? 5 : 1;
  return i % step === 0 || i === (trend.value?.points.length ?? 0) - 1;
}
function selectDays(value: number | string | boolean): void {
  days.value = Number(value);
  void load();
}

async function load(): Promise<void> {
  loading.value = true;
  const [trendRes, rank] = await Promise.allSettled([
    unwrap(systemApi.salesTrend(days.value)),
    unwrap(systemApi.salesRankReport()),
  ]);
  trend.value = trendRes.status === 'fulfilled' ? trendRes.value : null;
  // 后端 P4 排行报表桩返回单条 SalesRankReportVO；列表端点补齐后这里会自动展示多行
  const rankVo = rank.status === 'fulfilled' ? rank.value : null;
  ranks.value = rankVo ? [rankVo] : [];
  if (trendRes.status === 'rejected') {
    ElMessage.error('销售趋势加载失败');
  }
  loading.value = false;
}

onMounted(() => { void load(); });
</script>

<style scoped>
.trend-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.trend-toolbar__label {
  font-size: 13px;
  color: #6b7c75;
}
.trend-toolbar__range {
  font-size: 12px;
  color: #93a29c;
}
.metric { display: flex; flex-direction: column; gap: 6px; margin-bottom: 16px; }
.metric-label { color: #6b7c75; font-size: 13px; }
.metric-value { font-size: 24px; color: #12664f; }
.metric-sub { color: #93a29c; font-size: 12px; }
.section-title { margin: 0 0 14px; font-size: 15px; color: #33463f; }
.trend-chart {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  height: 180px;
  padding: 8px 4px 0;
  border-bottom: 1px solid #e5ebe8;
  overflow: hidden;
}
.trend-col {
  flex: 1 1 0;
  min-width: 2px;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  position: relative;
  cursor: default;
}
.trend-col__bar {
  width: 100%;
  min-height: 3px;
  border-radius: 3px 3px 0 0;
  background: #12664f;
  opacity: 0.72;
  transition: opacity 0.15s;
}
.trend-col:hover .trend-col__bar { opacity: 1; }
.trend-col__label {
  position: absolute;
  bottom: -20px;
  font-size: 10px;
  color: #93a29c;
  white-space: nowrap;
}
.bar-cell { display: flex; align-items: center; gap: 10px; }
.bar { height: 10px; width: 0; max-width: 100%; min-width: 4px; border-radius: 5px; background: #12664f; opacity: 0.65; flex: 0 0 auto; }
</style>
