<template>
  <div>
    <h2 class="sent-page-title">商品列表</h2>
    <div class="sent-panel">
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        selectable
        row-key="productId"
        exportable
        export-name="商品列表"
        @update:page="onPage"
        @update:size="onSize"
        @selection-change="selected = $event"
        @refresh="load"
      >
        <template #toolbar>
          <el-input
            v-model="query.keyword"
            placeholder="商品名称"
            clearable
            style="width: 200px"
            @keyup.enter="load"
          />
          <el-select v-model="query.categoryId" placeholder="分类" clearable style="width: 160px">
            <el-option
              v-for="c in flatCategories"
              :key="String(c.categoryId)"
              :label="c.name"
              :value="Number(c.categoryId)"
            />
          </el-select>
          <el-select v-model="query.onShelf" placeholder="上架状态" clearable style="width: 140px">
            <el-option label="已上架" :value="true" />
            <el-option label="已下架" :value="false" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="router.push('/product/edit')">新增商品</el-button>
          <el-button :disabled="!selected.length" @click="batchPublish(true)">批量上架</el-button>
          <el-button :disabled="!selected.length" @click="batchPublish(false)">批量下架</el-button>
        </template>

        <template #col-onShelf="{ row }">
          <StatusTag :label="row.onShelf ? '已上架' : '已下架'" :tone="row.onShelf ? 'success' : 'info'" />
        </template>

        <template #operations="{ row }">
          <el-button link type="primary" @click="router.push(`/product/edit?id=${row.productId}`)">
            编辑
          </el-button>
          <el-popconfirm title="确认删除该商品？" @confirm="remove(row)">
            <template #reference>
              <el-button link type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </SentTable>
    </div>
  </div>
</template>

<script setup lang="ts">
/** 商品列表（P6-25）：筛选 + 分页 + 列显隐/导出（SentTable）+ 批量上下架 */
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { unwrap } from '@/utils/http';
import { productApi } from '@/api';
import type { CategoryVO } from '@sent/shared';
import { useTable } from '@/composables/useTable';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

const router = useRouter();
const route = useRoute();
const columns: TableColumn[] = [
  { prop: 'productId', label: '商品 ID', width: 170 },
  { prop: 'name', label: '商品名称', minWidth: 180 },
  { prop: 'categoryName', label: '分类', width: 140 },
  { prop: 'skuCount', label: '规格数', width: 90, align: 'center' },
  { prop: 'discountSkuCount', label: '已配折扣规格', width: 130, align: 'center' },
  { prop: 'onShelf', label: '状态', width: 110, align: 'center' },
];

const selected = ref<any[]>([]);
const categories = ref<CategoryVO[]>([]);
const flatCategories = ref<CategoryVO[]>([]);
const query = reactive<{ keyword: string; categoryId?: number; onShelf?: boolean }>({
  keyword: '',
});
// immediate: false —— 首屏要先吃 URL 上的 categoryId 再拉列表，见文件末尾的 onMounted
const { rows, total, loading, page, size, load, onPage, onSize, onSearch } = useTable<any>({
  fetch: (params) => unwrap(productApi.list({
    ...params,
    keyword: query.keyword || undefined,
    categoryId: query.categoryId,
    onShelf: query.onShelf,
  })),
  defaultSize: 10,
  errorMessage: '商品列表加载失败',
  immediate: false,
});

function flatten(list: CategoryVO[]): CategoryVO[] {
  return list.flatMap((item) => [item, ...(item.children ? flatten(item.children) : [])]);
}

async function loadCategories(): Promise<void> {
  try {
    categories.value = await unwrap(productApi.categories());
    flatCategories.value = flatten(categories.value);
  } catch {
    categories.value = [];
    flatCategories.value = [];
  }
}

async function batchPublish(onShelf: boolean): Promise<void> {
  const targets = selected.value;
  if (!targets.length) return;
  try {
    await ElMessageBox.confirm(
      `确认将选中的 ${targets.length} 个商品${onShelf ? '上架' : '下架'}？`,
      '批量操作',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  const results = await Promise.allSettled(
    targets.map((row) => unwrap(productApi.publish(Number(row.productId), onShelf))),
  );
  const ok = results.filter((r) => r.status === 'fulfilled').length;
  ElMessage.success(`成功 ${ok} 个，失败 ${results.length - ok} 个`);
  selected.value = [];
  await load();
}

async function remove(row: any): Promise<void> {
  try {
    await unwrap(productApi.remove(Number(row.productId)));
    ElMessage.success('已删除');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '删除失败');
  }
}

onMounted(async () => {
  // 从「分类管理」跳过来时带上 categoryId，直接落到该分类的商品
  const fromQuery = route.query.categoryId;
  if (fromQuery !== undefined && fromQuery !== null && String(fromQuery) !== '') {
    query.categoryId = Number(fromQuery);
  }
  await loadCategories();
  await load();
});
</script>
