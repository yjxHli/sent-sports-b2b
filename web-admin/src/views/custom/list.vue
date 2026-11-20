<template>
  <div>
    <h2 class="sent-page-title">定制列表</h2>
    <div class="sent-panel">
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :selectable="deleteMode"
        row-key="customOrderId"
        exportable
        export-name="定制订单"
        :operation-width="150"
        @selection-change="selected = $event"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar-actions>
          <BatchDeleteButton
            v-model:active="deleteMode"
            :ids="selected.map((r: any) => String(r.customOrderId))"
            :summary="`已选择 ${selected.length} 张定制单：${selected.map((r: any) => r.customOrderId).join('、')}`"
            :delete-fn="(ids, config) => unwrap(customApi.batchDelete(ids, config))"
            @done="onDeleted"
          />
        </template>
        <template #toolbar>
          <el-input
            v-model="query.customNo"
            placeholder="定制单号"
            clearable
            style="width: 200px"
            @keyup.enter="onSearch"
          />
          <el-select v-model="query.status" placeholder="定制状态" clearable style="width: 170px">
            <el-option
              v-for="s in customStatuses"
              :key="s"
              :label="statusLabel(s)"
              :value="s"
            />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="router.push('/custom/review')">进入审核台</el-button>
        </template>

        <template #col-status="{ row }"><StatusTag :status="row.status" /></template>
        <template #col-reviewDeadlineMillis="{ row }">
          <span :class="{ overdue: isOverdue(row.reviewDeadlineMillis) }">
            {{ fmtDateTime(row.reviewDeadlineMillis) }}
          </span>
        </template>

        <template #operations="{ row }">
          <el-button
            link
            type="primary"
            @click="router.push(`/custom/review?id=${row.customOrderId}`)"
          >
            审核
          </el-button>
        </template>
      </SentTable>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 定制列表（P6-37）：状态筛选 + 分页 + 导出；审核截止逾期标红。
 * 列表与审核台共用 customApi.pending，避免另造一份查询契约。
 */
import { reactive } from 'vue';
import { useRouter } from 'vue-router';
import { unwrap } from '@/utils/http';
import { customApi } from '@/api';
import { CustomStatus, fmtDateTime, statusLabel } from '@sent/shared';
import { useTable } from '@/composables/useTable';
import { useBatchDelete } from '@/composables/useBatchDelete';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import BatchDeleteButton from '@/components/BatchDeleteButton.vue';
import type { TableColumn } from '@/components/types';

const router = useRouter();
const customStatuses = Object.values(CustomStatus);

const columns: TableColumn[] = [
  { prop: 'customOrderId', label: '定制单号', minWidth: 190 },
  { prop: 'status', label: '状态', width: 130, align: 'center' },
  { prop: 'version', label: '图稿版本', width: 110, align: 'center' },
  { prop: 'rejectCount', label: '驳回次数', width: 110, align: 'center' },
  { prop: 'reviewDeadlineMillis', label: '审核截止', width: 170 },
  { prop: 'fileId', label: '图稿文件', width: 150 },
];

const query = reactive<{ status?: string; customNo?: string }>({});
const { rows, total, loading, page, size, load, onPage, onSize, onSearch } = useTable<any>({
  fetch: (params) => unwrap(customApi.pending({
    ...params,
    customNo: (query.customNo ?? '').trim() || undefined,
  })),
  // 后端 pending 暂不按状态过滤，前端仅做展示层筛选，不伪造服务端查询能力
  transform: ({ list, total: count }) => {
    if (!query.status) return { list, total: count };
    const filtered = list.filter((item: any) => item.status === query.status);
    return { list: filtered, total: filtered.length };
  },
  errorMessage: '定制订单加载失败',
});
const { deleteMode, selected, onDeleted } = useBatchDelete(load);

function isOverdue(deadline: number | undefined): boolean {
  return typeof deadline === 'number' && deadline > 0 && Date.now() > deadline;
}
</script>

<style scoped>
.overdue { color: #d03050; font-weight: 600; }
</style>
