<template>
  <div>
    <h2 class="sent-page-title">操作日志</h2>
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
        row-key="logId"
        exportable
        export-name="操作日志"
        :export-rows="exportAll"
        @selection-change="selected = $event"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar-actions>
          <BatchDeleteButton
            v-model:active="deleteMode"
            :ids="selected.map((r: any) => String(r.logId))"
            :summary="`已选择 ${selected.length} 条操作日志`"
            :delete-fn="(ids, config) => unwrap(systemApi.batchDeleteLogs(ids, config))"
            @done="onDeleted"
          />
        </template>
        <template #toolbar>
          <el-input
            v-model="keyword"
            placeholder="操作人 / 动作 / 单号 / 原因"
            clearable
            style="width: 240px"
            @keyup.enter="onSearch"
          />
          <el-input
            v-model="module"
            placeholder="模块（如 ORDER）"
            clearable
            style="width: 160px"
            @keyup.enter="onSearch"
          />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="onReset">重置</el-button>
        </template>
      </SentTable>
    </div>
  </div>
</template>

<script setup lang="ts">
/** 操作日志（P6-51）：只读审计列表，筛选条件下推服务端，真查 sys_operation_log。 */
import { ref } from 'vue';
import { unwrap } from '@/utils/http';
import { systemApi } from '@/api';
import { fmtDateTime } from '@sent/shared';
import { useTable } from '@/composables/useTable';
import SentTable from '@/components/SentTable.vue';
import BatchDeleteButton from '@/components/BatchDeleteButton.vue';
import { useBatchDelete } from '@/composables/useBatchDelete';
import type { TableColumn } from '@/components/types';

const columns: TableColumn[] = [
  { prop: 'logId', label: '日志 ID', width: 110 },
  { prop: 'operator', label: '操作人', width: 140 },
  { prop: 'module', label: '模块', width: 120 },
  { prop: 'action', label: '操作动作', width: 140 },
  { prop: 'targetId', label: '业务单号', minWidth: 190 },
  { prop: 'reason', label: '原因 / 备注', minWidth: 200 },
  { prop: 'timestampMillis', label: '操作时间', width: 180,
    formatter: (_r, v) => fmtDateTime(v) },
];

const keyword = ref('');
const module = ref('');
const { rows, total, loading, page, size, load, onPage, onSize, onSearch,
  fetchAll: exportAll } = useTable<any>({
  fetch: (params) => unwrap(systemApi.logs({
    ...params,
    keyword: keyword.value.trim() || undefined,
    module: module.value.trim() || undefined,
  })),
  errorMessage: '日志加载失败',
});
const { deleteMode, selected, onDeleted } = useBatchDelete(load);

function onReset(): void {
  keyword.value = '';
  module.value = '';
  onSearch();
}
</script>
