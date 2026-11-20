<template>
  <div>
    <h2 class="sent-page-title">业务参数</h2>
    <div class="sent-panel">
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        exportable
        export-name="业务参数"
        :operation-width="130"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar>
          <el-input
            v-model="keyword"
            placeholder="参数键过滤"
            clearable
            style="width: 220px"
            @keyup.enter="onSearch"
          />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="openCreate">新增参数</el-button>
          <span class="sent-hint">参数值为字符串，金额/天数类由后端按各自单位解析</span>
        </template>
        <template #operations="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        </template>
      </SentTable>
    </div>

    <el-dialog v-model="formVisible" :title="isNew ? '新增参数' : '编辑参数'" width="460px">
      <el-form label-width="100px">
        <el-form-item label="参数键">
          <el-input v-model="paramForm.paramKey" :disabled="!isNew" placeholder="如 order.timeout.minutes" />
        </el-form-item>
        <el-form-item label="参数值">
          <el-input v-model="paramForm.paramValue" placeholder="字符串值" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/** 业务参数（P6-50）：键值对维护，值一律按字符串提交，单位解释权归后端。 */
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { systemApi, type ParamVO } from '@/api';
import { useTable } from '@/composables/useTable';
import SentTable from '@/components/SentTable.vue';
import type { TableColumn } from '@/components/types';

const columns: TableColumn[] = [
  { prop: 'paramKey', label: '参数键', minWidth: 240 },
  { prop: 'paramValue', label: '参数值', minWidth: 240 },
];

const keyword = ref('');
const submitting = ref(false);
/** 参数量小，一次性拉取后展示层切片；页码与每页条数仍由 usePagination 记忆。 */
const { rows, total, loading, page, size, load, onPage, onSize, onSearch } = useTable<ParamVO>({
  mode: 'client',
  fetch: () => unwrap(systemApi.params()),
  // 关键字只在前端过滤：业务参数一共十几条，不值得为此加一个后端查询参数
  transform: ({ list }) => {
    const kw = keyword.value.trim();
    const filtered = kw ? list.filter((item) => item.paramKey.includes(kw)) : list;
    return { list: filtered, total: filtered.length };
  },
  errorMessage: '参数列表加载失败',
});

const formVisible = ref(false);
const isNew = ref(true);
const paramForm = reactive<{ paramKey: string; paramValue: string }>({ paramKey: '', paramValue: '' });

function openCreate(): void {
  isNew.value = true;
  paramForm.paramKey = '';
  paramForm.paramValue = '';
  formVisible.value = true;
}

function openEdit(row: ParamVO): void {
  isNew.value = false;
  paramForm.paramKey = row.paramKey;
  paramForm.paramValue = row.paramValue;
  formVisible.value = true;
}

async function submit(): Promise<void> {
  if (!paramForm.paramKey.trim() || !paramForm.paramValue.trim()) {
    ElMessage.warning('请填写参数键与参数值');
    return;
  }
  submitting.value = true;
  try {
    await unwrap(systemApi.upsertParam({
      paramKey: paramForm.paramKey.trim(),
      paramValue: paramForm.paramValue.trim(),
    }));
    ElMessage.success('参数已保存');
    formVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '保存失败');
  } finally {
    submitting.value = false;
  }
}

</script>
