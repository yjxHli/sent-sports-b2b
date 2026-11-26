<template>
  <div class="sent-table">
    <div class="sent-table-toolbar">
      <div class="sent-table-filters">
        <slot name="toolbar" />
      </div>
      <div class="sent-table-actions">
        <!--
          删除态的两个「勾选范围」入口（2026-11-26 用户要求）：
          全选本页 = 只处理当前页；全选全部 = 按当前筛选拉全量，配合表头全选的跨页口径。
          只在选择列出现时显示；是否提供「全选全部」由父页面是否传 selectAllRows 决定。
        -->
        <template v-if="selectable">
          <el-button size="small" data-testid="batch-select-page" @click="selectPageRows">
            全选本页
          </el-button>
          <el-button
            v-if="canSelectAll"
            size="small"
            data-testid="batch-select-all"
            @click="selectAllAcrossPages"
          >
            全选全部
          </el-button>
        </template>
        <slot name="toolbar-actions" />
        <el-button class="sent-table__tool" text size="small" @click="emit('refresh')">刷新</el-button>
        <el-dropdown v-if="columnToggle" trigger="click" :hide-on-click="false">
          <el-button class="sent-table__tool" text size="small">列显隐</el-button>
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
        <el-button
          v-if="exportable"
          class="sent-table__tool"
          text
          size="small"
          :loading="exporting"
          @click="exportCsv"
        >
          {{ exporting ? '导出中…' : '导出 CSV' }}
        </el-button>
      </div>
    </div>

    <el-table
      ref="tableRef"
      :data="rows"
      v-loading="loading"
      :row-key="rowKey"
      @select-all="onSelectAll"
      @selection-change="onSelectionChange"
    >
      <!--
        2026-11-26 用户要求：表头全选 = 当前筛选条件下「全部记录」，不是本页。
        Element Plus 的 selection 列表头不能替换（自定义 header 槽会被忽略），
        所以在 @select-all 里再拉全量、把跨页集合交给父页面。
      -->
      <el-table-column v-if="selectable" type="selection" width="48" :selectable="selectableRow" />
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
          <!-- index 只作为附加 slot prop，老列的具名插槽不受影响；序号列按当前页连续编号 -->
          <slot
            :name="`col-${col.prop}`"
            :row="scope.row"
            :value="scope.row[col.prop]"
            :index="scope.$index"
          >
            {{ textOf(col, scope.row) }}
          </slot>
        </template>
      </el-table-column>
      <el-table-column
        v-if="$slots.operations"
        label="操作"
        :width="operationWidth"
        fixed="right"
        align="left"
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
import { computed, nextTick, ref, watch } from 'vue';
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
  /**
   * 「导出整个列表」：传了它就按它返回的全量行导出（通常是循环拉完所有分页），
   * 不传则只导出当前页 —— 券模板/发放记录要「整张表下载」，靠这个钩子拿全量。
   */
  exportRows?: () => Promise<any[]>;
  /**
   * 「全选全部」的数据源（2026-11-26）：删除态点表头全选框时用它拉当前筛选下的全部行。
   * 不传则退化为「全选本页」，行为与改造前一致。通常直接传 useTable 的 fetchAll。
   */
  selectAllRows?: () => Promise<any[]>;
  /** 行是否可勾选（如「在途订单不可删」），不传则全部可勾 */
  selectableRow?: (row: any) => boolean;
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
  selectAllRows: undefined,
  selectableRow: undefined,
});

const emit = defineEmits<{
  (e: 'update:page', value: number): void;
  (e: 'update:size', value: number): void;
  (e: 'selection-change', rows: any[]): void;
  (e: 'refresh'): void;
}>();

const hidden = ref<string[]>([]);
/** 导出全量时的按钮 loading，避免连点重复拉页 */
const exporting = ref(false);
/**
 * JacksonConfig 把 Long 序列化成字符串，接口分页的 total 到前端会变成 "62"。
 * Element Plus 的 total 只收 Number，传字符串会触发类型警告并让分页器渲染异常，这里统一兜底。
 */
const normalizedTotal = computed(() => Number(props.total) || 0);
const visibleColumns = computed(() => props.columns.filter((c) => !hidden.value.includes(c.prop)));
/** withDefaults 给可选函数塞了 undefined 默认值，模板里直接判断函数会触发 TS2774，统一走这里 */
const canSelectAll = computed(() => typeof props.selectAllRows === 'function');

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
  // 代码触发的勾选（全选全部 / 翻页同步）不回灌自身状态，否则会互相覆盖成「只剩当前页」
  if (syncing || pageSyncPending) return;
  if (!allMode.value) {
    selectedKeys.value = new Set(rows.map(keyOf));
    emit('selection-change', rows);
    return;
  }
  // 全量模式：当前页的增删要作用在「已选全量集合」上，而不是替换它
  const pageKeys = new Set(props.rows.map(keyOf));
  const pageSelected = new Set(rows.map(keyOf));
  const merged = allRows.value.filter((row) => {
    const key = keyOf(row);
    return !pageKeys.has(key) || pageSelected.has(key);
  });
  // 全选后新插入、当前页新出现的行也要补进来
  for (const row of rows) {
    if (!merged.some((m) => keyOf(m) === keyOf(row))) merged.push(row);
  }
  allRows.value = merged;
  selectedKeys.value = new Set(merged.map(keyOf));
  emit('selection-change', merged);
}

/** 跨页全选状态（2026-11-26）：点表头全选 = 选中当前筛选下全部记录 */
const tableRef = ref<any>(null);
const allMode = ref(false);
const allRows = ref<any[]>([]);
const selectedKeys = ref<Set<string>>(new Set());
let syncing = false;
/** 翻页瞬间 el-table 会先清空本页勾选并触发 selection-change，这段时间的变更必须忽略 */
let pageSyncPending = false;

function keyOf(row: any): string {
  return String(row?.[props.rowKey] ?? '');
}

function clearAllSelection(): void {
  allMode.value = false;
  allRows.value = [];
  selectedKeys.value = new Set();
  syncing = true;
  tableRef.value?.clearSelection();
  syncing = false;
}

/**
 * 表头全选（2026-11-26）：先让 el-table 勾上本页（原生行为），再按同一筛选条件拉全量，
 * 把「整张表」的集合交给父页面。取消全选（rows 为空）则整体清空。
 */
async function onSelectAll(rows: any[]): Promise<void> {
  if (syncing) return;
  if (!rows.length) {
    clearAllSelection();
    emit('selection-change', []);
    return;
  }
  if (!props.selectAllRows) {
    emit('selection-change', rows);
    return;
  }
  syncing = true;
  try {
    const all = (await props.selectAllRows()) ?? [];
    allMode.value = true;
    allRows.value = all;
    selectedKeys.value = new Set(all.map(keyOf));
  } finally {
    syncing = false;
  }
  emit('selection-change', allRows.value);
}

/**
 * 显式指定勾选范围（2026-11-26）：删除态下「全选本页 / 全选全部」共用这一条装配路径。
 * 清选与回填都会触发 el-table 的 selection-change，syncing 期间不回灌父页面，
 * 最后主动 emit 一次最终集合，避免中间态把「已选 N 条」刷成 0。
 */
async function applySelection(rows: any[], acrossPages: boolean): Promise<void> {
  allMode.value = acrossPages;
  allRows.value = acrossPages ? rows : [];
  selectedKeys.value = new Set(rows.map(keyOf));
  syncing = true;
  tableRef.value?.clearSelection();
  for (const row of props.rows) {
    tableRef.value?.toggleRowSelection(row, selectedKeys.value.has(keyOf(row)));
  }
  await nextTick();
  syncing = false;
  emit('selection-change', rows);
}

function selectPageRows(): void {
  void applySelection([...props.rows], false);
}

async function selectAllAcrossPages(): Promise<void> {
  const all = props.selectAllRows ? (await props.selectAllRows()) ?? [] : [...props.rows];
  await applySelection(all, true);
}

/** 翻页后按跨页选中的 key 恢复当前页勾选状态，避免「全选了但新页一个都没勾」 */
watch(() => props.rows, async (rows) => {
  if (!allMode.value || !rows.length) return;
  pageSyncPending = true;
  await nextTick();
  syncing = true;
  for (const row of rows) tableRef.value?.toggleRowSelection(row, selectedKeys.value.has(keyOf(row)));
  syncing = false;
  pageSyncPending = false;
  // 父页面的勾选集合保持「全量」，不因翻页缩成当前页
  emit('selection-change', allRows.value);
});

/** 退出删除态（selectable=false）时清空内部跨页选择，下次进入是干净的 */
watch(() => props.selectable, (on) => {
  if (!on) {
    clearAllSelection();
    emit('selection-change', []);
  }
});

function csvCell(value: string): string {
  return `"${value.replace(/"/g, '""')}"`;
}

/** 导出当前页可见列为 CSV（带 BOM，Excel 直接打开不乱码）；传了 exportRows 则导出全量 */
async function exportCsv(): Promise<void> {
  if (exporting.value) return;
  let rows = props.rows;
  if (props.exportRows) {
    exporting.value = true;
    try {
      rows = await props.exportRows();
    } catch {
      // 拉全量失败就退回当前页，至少别让按钮一直转
      rows = props.rows;
    } finally {
      exporting.value = false;
    }
  }
  const cols = visibleColumns.value;
  const header = cols.map((c) => csvCell(c.label)).join(',');
  const lines = rows.map((row) => cols.map((c) => csvCell(textOf(c, row))).join(','));
  const blob = new Blob(['\uFEFF' + [header, ...lines].join('\r\n')], {
    type: 'text/csv;charset=utf-8',
  });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = `${props.exportName}.csv`;
  anchor.click();
  // 立刻 revoke 会让部分 Chromium 版本在下载真正开始前失效（点完什么都没下到），
  // 延后释放，文件已经落到本地再回收 blob。
  setTimeout(() => URL.revokeObjectURL(url), 2000);
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
.sent-table-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
/* 工具按钮：刷新 / 列显隐 / 导出是维护动作，仍比「查询」主按钮轻一档，
   但文字必须到正文色阶（原 --sent-ink-3 在白卡上对比度只有 4.3:1，扫一眼看不清）。 */
.sent-table__tool {
  padding: 0 8px;
  color: var(--sent-ink-2);
  font-weight: var(--fw-medium);
}
.sent-table__tool:hover {
  color: var(--sent-primary);
  background: var(--sent-primary-soft);
}
.sent-table-pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
/**
 * 左右分栏的窄卡片（企业审核与授信）里，分页器「共 N 条 / 条每页 / 页码 / 前往」一行放不下时，
 * flex 会把靠左的「条/页」挤出容器左侧、甚至压到侧边栏下面，用户点都点不到。
 * 允许换行后所有控件始终在可视区内，宽屏下布局与原来一致（只有放不下才折行）。
 */
.sent-table-pager :deep(.el-pagination) {
  flex-wrap: wrap;
  justify-content: flex-end;
  row-gap: 6px;
  max-width: 100%;
}
</style>
