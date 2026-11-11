<template>
  <div>
    <h2 class="sent-page-title">操作日志</h2>
    <div class="sent-panel">
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        exportable
        export-name="操作日志"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
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
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { systemApi } from '@/api';
import { fmtDateTime } from '@sent/shared';
import { usePagination } from '@/composables/usePagination';
import SentTable from '@/components/SentTable.vue';
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

const rows = ref<any[]>([]);
const total = ref(0);
const { page, size } = usePagination();
const loading = ref(false);
const keyword = ref('');
const module = ref('');

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await unwrap(systemApi.logs({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined,
      module: module.value.trim() || undefined,
    }));
    rows.value = data?.list ?? [];
    total.value = data?.total ?? 0;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    ElMessage.error((error as Error)?.message || '日志加载失败');
  } finally {
    loading.value = false;
  }
}

function onSearch(): void { page.value = 1; void load(); }
function onReset(): void {
  keyword.value = '';
  module.value = '';
  page.value = 1;
  void load();
}
function onPage(value: number): void { page.value = value; void load(); }
function onSize(value: number): void { size.value = value; page.value = 1; void load(); }

onMounted(() => { void load(); });
</script>
