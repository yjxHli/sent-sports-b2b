<template>
  <div class="sent-table">
    <div class="sent-table-toolbar">
      <div class="sent-table-filters">
        <slot name="toolbar" />
      </div>
      <div class="sent-table-actions">
        <el-button size="small" @click="emit('refresh')">刷新</el-button>
        <el-dropdown v-if="columnToggle" trigger="click" :hide-on-click="false">
          <el-button size="small">列显隐</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="col in columns" :key="col.prop">
                <el-checkbox
                  :model-value="!hidden.includes(col.prop)"
                  @change="(v: boolean) => toggleColumn(col.prop, v)"
                >
                  {{ col.label }}
                </el-checkbox>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button v-if="exportable" size="small" @click="exportCsv">导出 CSV</el-button>
      </div>
    </div>

    <el-table
      :data="rows"
      v-loading="loading"
      border
      stripe
      :row-key="rowKey"
      @selection-change="onSelectionChange"
    >
      <el-table-column v-if="selectable" type="selection" width="48" />
      <el-table-column
        v-for="col in visibleColumns"
        :key="col.prop"
        :prop="col.prop"
        :label="col.label"
        :width="col.width"
        :min-width="col.minWidth"
        :align="col.align || 'left'"
        show-overflow-tooltip
      >
        <template #default="scope">
          <slot :name="`col-${col.prop}`" :row="scope.row" :value="scope.row[col.prop]">
            {{ textOf(col, scope.row) }}
          </slot>
        </template>
      </el-table-column>
      <el-table-column
        v-if="$slots.operations"
        label="操作"
        :width="operationWidth"
        fixed="right"
        align="center"
      >
        <template #default="scope">
          <slot name="operations" :row="scope.row" />
        </template>
      </el-table-column>
      <template #empty>
        <slot name="empty">
          <el-empty description="暂无数据" :image-size="70" />
        </slot>
      </template>
    </el-table>

    <div v-if="normalizedTotal > 0" class="sent-table-pager">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="normalizedTotal"
        :current-page="page"
        :page-size="size"
        :page-sizes="pageSizes"
        @current-change="(p: number) => emit('update:page', p)"
        @size-change="(s: number) => emit('update:size', s)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 列表页基座（P6-25 起复用）：分页、筛选区插槽、列显隐、CSV 导出、多选、行操作。
 * 对应 plan §9.4.5「列表页分页、筛选、列显隐、导出可用」验收项。
 * 只用 ElementPlus，不引入 pure-admin 的 RePureTableBar，避免为一张表拖入整套模板依赖。
 */
import { computed, ref } from 'vue';
import type { TableColumn } from './types';

const props = withDefaults(defineProps<{
  columns: TableColumn[];
  rows: any[];
  total?: number;
  page?: number;
  size?: number;
  loading?: boolean;
  selectable?: boolean;
  exportable?: boolean;
  columnToggle?: boolean;
  exportName?: string;
  operationWidth?: number | string;
  /** 每页条数可选项，默认含 10 / 15，供列表页自行设定翻页粒度 */
  pageSizes?: number[];
  /**
   * 行主键字段名，供多选/展开等状态识别行身份。
   * 默认 `id`；若列表主键叫 `productId`/`orderId` 之类，必须显式传入，
   * 否则每行 key 都是 undefined，el-table 会把所有行当成同一行（勾一个全勾）。
   */
  rowKey?: string;
}>(), {
  total: 0,
  page: 1,
  size: 20,
  loading: false,
  selectable: false,
  exportable: false,
  columnToggle: true,
  exportName: 'export',
  operationWidth: 160,
  rowKey: 'id',
  pageSizes: () => [10, 15, 20, 50, 100],
});

const emit = defineEmits<{
  (e: 'update:page', value: number): void;
  (e: 'update:size', value: number): void;
  (e: 'selection-change', rows: any[]): void;
  (e: 'refresh'): void;
}>();

const hidden = ref<string[]>([]);
/**
 * JacksonConfig 把 Long 序列化成字符串，接口分页的 total 到前端会变成 "62"。
 * Element Plus 的 total 只收 Number，传字符串会触发类型警告并让分页器渲染异常，这里统一兜底。
 */
const normalizedTotal = computed(() => Number(props.total) || 0);
const visibleColumns = computed(() => props.columns.filter((c) => !hidden.value.includes(c.prop)));

function toggleColumn(prop: string, visible: boolean): void {
  hidden.value = visible
    ? hidden.value.filter((p) => p !== prop)
    : [...hidden.value, prop];
}

function textOf(col: TableColumn, row: any): string {
  const value = row?.[col.prop];
  if (col.formatter) return col.formatter(row, value);
  if (value === null || value === undefined || value === '') return '-';
  return typeof value === 'object' ? JSON.stringify(value) : String(value);
}

function onSelectionChange(rows: any[]): void {
  emit('selection-change', rows);
}

function csvCell(value: string): string {
  return `"${value.replace(/"/g, '""')}"`;
}

/** 导出当前页可见列为 CSV（带 BOM，Excel 直接打开不乱码） */
function exportCsv(): void {
  const cols = visibleColumns.value;
  const header = cols.map((c) => csvCell(c.label)).join(',');
  const lines = props.rows.map((row) => cols.map((c) => csvCell(textOf(c, row))).join(','));
  const blob = new Blob(['\uFEFF' + [header, ...lines].join('\r\n')], {
    type: 'text/csv;charset=utf-8',
  });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `${props.exportName}.csv`;
  anchor.click();
  URL.revokeObjectURL(url);
}
</script>

<style scoped>
.sent-table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.sent-table-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.sent-table-actions { display: flex; gap: 8px; }
.sent-table-pager { display: flex; justify-content: flex-end; margin-top: 14px; }
</style>
