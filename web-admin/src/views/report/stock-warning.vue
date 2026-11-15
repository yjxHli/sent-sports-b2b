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
        摘要取缺口最大的一条告急 SKU；下方清单来自库存四口径实时列表，「仅看低于安全库存」由后端 SQL 下推。
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
          <el-checkbox v-model="onlyWarning" @change="onWarningChange">仅看低于安全库存</el-checkbox>
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
 *
 * 2026-11-12 收口：原实现默认勾选「仅看低于安全库存」，却在前端把**当前页**再 filter 一遍，
 * 总数还用过滤后的行数 —— 单页上限 100 会漏数据；库里没有告急 SKU 时整页「暂无数据」。
 * 现在筛选条件经 warningOnly 下推到 SQL（列表与 count 同条件），默认展示全量台账并用状态列
 * 标出哪些告急，需要时再勾选只看预警。
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
/** 默认展示全量台账；改勾选时由后端重新分页，不再前端截断 */
const onlyWarning = ref(false);

function isWarning(row: any): boolean {
  if (row.warning === true) return true;
  return Number(row.availableQty ?? 0) <= Number(row.safeQty ?? 0);
}

async function load(): Promise<void> {
  loading.value = true;
  const [warn, stocks] = await Promise.allSettled([
    unwrap(systemApi.stockWarningReport()),
    unwrap(stockApi.list({
      page: page.value,
      size: size.value,
      skuId: skuId.value,
      warningOnly: onlyWarning.value,
    })),
  ]);
  summary.value = warn.status === 'fulfilled' ? warn.value : null;
  rows.value = stocks.status === 'fulfilled' ? (stocks.value?.list ?? []) : [];
  total.value = stocks.status === 'fulfilled' ? Number(stocks.value?.total ?? 0) : 0;
  if (stocks.status === 'rejected') ElMessage.error('库存列表加载失败');
  loading.value = false;
}

function onSearch(): void { page.value = 1; void load(); }
/** 换筛选条件必须回到第 1 页，否则会停在一个不存在的页码上看到空表 */
function onWarningChange(): void { page.value = 1; void load(); }
function onPage(value: number): void { page.value = value; void load(); }
function onSize(value: number): void { size.value = value; page.value = 1; void load(); }

onMounted(() => { void load(); });
</script>

<style scoped>
.section-title { margin: 0 0 14px; font-size: 15px; color: #33463f; }
.danger { color: #d03050; font-weight: 600; }
</style>
