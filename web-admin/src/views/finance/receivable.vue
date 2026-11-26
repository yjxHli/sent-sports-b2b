<template>
  <div>
    <h2 class="sent-page-title">应收台账</h2>
    <div class="sent-panel">
      <SentTable
        :select-all-rows="exportAll"
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :selectable="deleteMode"
        row-key="receivableId"
        exportable
        export-name="应收台账"
        :export-rows="exportAll"
        :operation-width="120"
        @selection-change="selected = $event"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar-actions>
          <BatchDeleteButton
            v-model:active="deleteMode"
            :ids="selected.map((r: any) => String(r.receivableId))"
            :summary="`已选择 ${selected.length} 条应收记录`"
            :delete-fn="(ids, config) => unwrap(financeApi.batchDeleteReceivables(ids, config))"
            @done="onDeleted"
          />
        </template>
        <template #toolbar>
          <el-input-number
            v-model="customerId"
            :min="1"
            :controls="false"
            placeholder="客户 ID"
            style="width: 160px"
          />
          <el-input
            v-model="orderNo"
            placeholder="订单号"
            clearable
            style="width: 200px"
            @keyup.enter="onSearch"
          />
          <el-select v-model="status" placeholder="应收状态" clearable style="width: 150px">
            <el-option
              v-for="s in Object.values(ReceivableStatus)"
              :key="s"
              :label="statusLabel(s)"
              :value="s"
            />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="router.push('/finance/payment-record')">去登记回款</el-button>
        </template>

        <template #col-status="{ row }"><StatusTag :status="row.status" /></template>
        <template #col-overdueDays="{ row }">
          <span :class="{ overdue: Number(row.overdueDays) > 0 }">
            {{ row.overdueDays ?? 0 }} 天
          </span>
        </template>

        <template #operations="{ row }">
          <el-button link type="primary" @click="goWriteOff(row)">核销</el-button>
        </template>
      </SentTable>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 应收台账（P6-44）：四列金额口径 + 逾期天数标红；核销动作统一跳到回款页完成，
 * 避免在台账页重复实现核销分配逻辑（后端 WriteOffAllocator 才是唯一真源）。
 */
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { unwrap } from '@/utils/http';
import { financeApi } from '@/api';
import { ReceivableStatus, fmtDate, fmtMoney, statusLabel } from '@sent/shared';
import { useTable } from '@/composables/useTable';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import BatchDeleteButton from '@/components/BatchDeleteButton.vue';
import { useBatchDelete } from '@/composables/useBatchDelete';
import type { TableColumn } from '@/components/types';

const router = useRouter();
const columns: TableColumn[] = [
  { prop: 'receivableId', label: '应收 ID', width: 130 },
  { prop: 'orderNo', label: '订单号', minWidth: 180 },
  { prop: 'customerId', label: '客户 ID', width: 120 },
  { prop: 'amountCent', label: '应收金额', width: 140, align: 'right',
    formatter: (_r, v) => fmtMoney(v) },
  { prop: 'writtenOffCent', label: '已核销', width: 140, align: 'right',
    formatter: (_r, v) => fmtMoney(v) },
  { prop: 'outstanding', label: '未核销', width: 140, align: 'right',
    formatter: (row) => fmtMoney(Number(row.amountCent ?? 0) - Number(row.writtenOffCent ?? 0)) },
  { prop: 'dueAt', label: '到期日', width: 130, formatter: (_r, v) => fmtDate(v) },
  { prop: 'overdueDays', label: '逾期', width: 100, align: 'center' },
  { prop: 'status', label: '状态', width: 120, align: 'center' },
];

const customerId = ref<number | undefined>(undefined);
const status = ref<string>('');
/** 订单号模糊搜索：台账动辄几百行，必须下推 SQL 收敛 */
const orderNo = ref('');
const { rows, total, loading, page, size, load, onPage, onSize, onSearch,
  fetchAll: exportAll } = useTable<any>({
  fetch: (params) => unwrap(financeApi.receivables({
    ...params,
    customerId: customerId.value,
    status: status.value || undefined,
    orderNo: orderNo.value.trim() || undefined,
  })),
  // 状态已由后端按 status 过滤，这里只兜底剔除服务端漏掉的异常行（保持行数与 total 一致）
  transform: ({ list, total: count }) => {
    if (!status.value) return { list, total: count };
    const filtered = list.filter((item: any) => item.status === status.value);
    return { list: filtered, total: filtered.length };
  },
  errorMessage: '应收台账加载失败',
});
const { deleteMode, selected, onDeleted } = useBatchDelete(load);

function goWriteOff(row: any): void {
  void router.push({
    path: '/finance/payment-record',
    query: { customerId: String(row.customerId), receivableId: String(row.receivableId) },
  });
}
</script>

<style scoped>
.overdue { color: #d03050; font-weight: 600; }
</style>
