<template>
  <div>
    <h2 class="sent-page-title">库存预警</h2>

    <div class="sent-panel">
      <h3 class="section-title">预警摘要</h3>
      <el-descriptions :column="4" border v-loading="loading">
        <el-descriptions-item label="SKU">{{ summary?.skuId ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="商品">{{ summary?.skuName ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="可用量">
          <span class="danger">{{ summary?.availableQty ?? '--' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="预警阈值">{{ summary?.threshold ?? '--' }}</el-descriptions-item>
      </el-descriptions>
      <p class="sent-hint">
        后端 /admin/reports/stock-warning 返回单条汇总（工作台同口径）；下方清单来自库存四口径实时列表。
      </p>
    </div>

    <div class="sent-panel">
      <h3 class="section-title">预警清单</h3>
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        exportable
        export-name="库存预警"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar>
          <el-input-number
            v-model="skuId"
            :min="1"
            :controls="false"
            placeholder="SKU ID"
            style="width: 160px"
          />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="router.push('/stock/inbound')">去手工入库</el-button>
          <el-checkbox v-model="onlyWarning" @change="load">仅看低于安全库存</el-checkbox>
        </template>
        <template #col-warning="{ row }">
          <StatusTag
            :label="isWarning(row) ? '预警' : '正常'"
            :tone="isWarning(row) ? 'danger' : 'success'"
          />
        </template>
      </SentTable>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 库存预警（P6-53）：单条汇总 + 实时清单。
 * 预警判定在展示层用 availableQty <= safeQty（与四口径一致），后端 warning 字段优先。
 */
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { stockApi, systemApi, type StockWarningVO } from '@/api';
import { fmtQty } from '@sent/shared';
import { usePagination } from '@/composables/usePagination';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

const router = useRouter();
const columns: TableColumn[] = [
  { prop: 'skuId', label: 'SKU ID', width: 130 },
  { prop: 'skuName', label: '商品', minWidth: 180 },
  { prop: 'physicalQty', label: '实物', width: 110, align: 'right', formatter: (_r, v) => fmtQty(v) },
  { prop: 'lockedQty', label: '锁定', width: 110, align: 'right', formatter: (_r, v) => fmtQty(v) },
  { prop: 'pendingQty', label: '待发', width: 110, align: 'right', formatter: (_r, v) => fmtQty(v) },
  { prop: 'frozenQty', label: '冻结', width: 110, align: 'right', formatter: (_r, v) => fmtQty(v) },
  { prop: 'availableQty', label: '可用', width: 110, align: 'right', formatter: (_r, v) => fmtQty(v) },
  { prop: 'safeQty', label: '安全库存', width: 110, align: 'right', formatter: (_r, v) => fmtQty(v) },
  { prop: 'warning', label: '状态', width: 110, align: 'center' },
];

const summary = ref<StockWarningVO | null>(null);
const rows = ref<any[]>([]);
const total = ref(0);
const { page, size } = usePagination();
const loading = ref(false);
const skuId = ref<number | undefined>(undefined);
const onlyWarning = ref(true);

function isWarning(row: any): boolean {
  if (row.warning === true) return true;
  return Number(row.availableQty ?? 0) <= Number(row.safeQty ?? 0);
}

async function load(): Promise<void> {
  loading.value = true;
  const [warn, stocks] = await Promise.allSettled([
    unwrap(systemApi.stockWarningReport()),
    unwrap(stockApi.list({ page: page.value, size: size.value, skuId: skuId.value })),
  ]);
  summary.value = warn.status === 'fulfilled' ? warn.value : null;
  const all = stocks.status === 'fulfilled' ? (stocks.value?.list ?? []) : [];
  rows.value = onlyWarning.value ? all.filter(isWarning) : all;
  total.value = onlyWarning.value ? rows.value.length : (stocks.status === 'fulfilled' ? (stocks.value?.total ?? 0) : 0);
  if (stocks.status === 'rejected') ElMessage.error('库存列表加载失败');
  loading.value = false;
}

function onSearch(): void { page.value = 1; void load(); }
function onPage(value: number): void { page.value = value; void load(); }
function onSize(value: number): void { size.value = value; page.value = 1; void load(); }

onMounted(() => { void load(); });
</script>

<style scoped>
.section-title { margin: 0 0 14px; font-size: 15px; color: #33463f; }
.danger { color: #d03050; font-weight: 600; }
</style>
