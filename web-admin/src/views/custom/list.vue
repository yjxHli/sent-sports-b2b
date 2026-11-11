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
        exportable
        export-name="定制订单"
        :operation-width="150"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar>
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
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { customApi } from '@/api';
import { CustomStatus, fmtDateTime, statusLabel } from '@sent/shared';
import { usePagination } from '@/composables/usePagination';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
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

const rows = ref<any[]>([]);
const total = ref(0);
const { page, size } = usePagination();
const loading = ref(false);
const query = reactive<{ status?: string }>({});

function isOverdue(deadline: number | undefined): boolean {
  return typeof deadline === 'number' && deadline > 0 && Date.now() > deadline;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await unwrap(customApi.pending({ page: page.value, size: size.value }));
    const list = data?.list ?? [];
    // 后端 pending 暂不按状态过滤，前端仅做展示层筛选，不伪造服务端查询能力
    rows.value = query.status ? list.filter((item: any) => item.status === query.status) : list;
    total.value = query.status ? rows.value.length : (data?.total ?? 0);
  } catch (error) {
    rows.value = [];
    total.value = 0;
    ElMessage.error((error as Error)?.message || '定制订单加载失败');
  } finally {
    loading.value = false;
  }
}

function onSearch(): void {
  page.value = 1;
  void load();
}
function onPage(value: number): void {
  page.value = value;
  void load();
}
function onSize(value: number): void {
  size.value = value;
  page.value = 1;
  void load();
}

onMounted(() => { void load(); });
</script>

<style scoped>
.overdue { color: #d03050; font-weight: 600; }
</style>
