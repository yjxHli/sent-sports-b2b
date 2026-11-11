<template>
  <div>
    <h2 class="sent-page-title">库存列表</h2>
    <div class="sent-panel">
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :operation-width="92"
        exportable
        export-name="库存台账"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar>
          <el-input v-model="skuKeyword" placeholder="SKU ID" clearable style="width: 180px" @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="router.push('/stock/inbound')">手工入库</el-button>
          <el-button @click="router.push('/stock/loss')">报损</el-button>
          <el-button @click="router.push('/stock/check')">盘点</el-button>
        </template>

        <template #col-availableQty="{ row }">
          <span :class="{ 'stock-warn': isWarning(row) }">{{ row.availableQty }}</span>
        </template>
        <template #col-warning="{ row }">
          <StatusTag
            :label="isWarning(row) ? '低于安全库存' : '正常'"
            :tone="isWarning(row) ? 'danger' : 'success'"
          />
        </template>

        <template #operations="{ row }">
          <el-button link type="primary" @click="router.push(`/stock/inbound?skuId=${row.skuId}`)">入库</el-button>
          <el-button link @click="router.push(`/stock/check?skuId=${row.skuId}`)">盘点</el-button>
        </template>
      </SentTable>
    </div>
  </div>
</template>

<script setup lang="ts">
/** 库存列表（P6-29）：四口径 + 可用量 + 安全库存，低于安全库存整行标红 */
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { stockApi } from '@/api';
import { usePagination } from '@/composables/usePagination';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

const router = useRouter();
const columns: TableColumn[] = [
  { prop: 'skuId', label: 'SKU ID', width: 84 },
  { prop: 'skuName', label: 'SKU 名称', minWidth: 150 },
  { prop: 'physicalQty', label: '实物', width: 58, align: 'center' },
  { prop: 'lockedQty', label: '锁定', width: 58, align: 'center' },
  { prop: 'pendingQty', label: '待发', width: 58, align: 'center' },
  { prop: 'frozenQty', label: '冻结', width: 58, align: 'center' },
  { prop: 'availableQty', label: '可用', width: 62, align: 'center' },
  { prop: 'safeQty', label: '安全库存', width: 76, align: 'center' },
  { prop: 'warning', label: '预警', width: 86, align: 'center' },
];

const rows = ref<any[]>([]);
const total = ref(0);
const { page, size } = usePagination();
const loading = ref(false);
const skuKeyword = ref('');

function isWarning(row: any): boolean {
  return Number(row.availableQty) <= Number(row.safeQty);
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const skuId = skuKeyword.value ? Number(skuKeyword.value) : undefined;
    const data = await unwrap(stockApi.list({
      page: page.value,
      size: size.value,
      skuId: Number.isFinite(skuId) ? skuId : undefined,
    }));
    rows.value = data?.list ?? [];
    total.value = data?.total ?? 0;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    ElMessage.error((error as Error)?.message || '库存台账加载失败');
  } finally {
    loading.value = false;
  }
}

function onSearch(): void { page.value = 1; void load(); }
function onPage(value: number): void { page.value = value; void load(); }
function onSize(value: number): void { size.value = value; page.value = 1; void load(); }

onMounted(() => { void load(); });
</script>

<style scoped>
.stock-warn { color: #d03050; font-weight: 700; }
</style>
