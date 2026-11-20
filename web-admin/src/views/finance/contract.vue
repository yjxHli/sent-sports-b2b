<template>
  <div>
    <h2 class="sent-page-title">合同管理</h2>
    <div class="sent-panel">
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :selectable="deleteMode"
        row-key="contractId"
        exportable
        export-name="合同列表"
        @selection-change="selected = $event"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar-actions>
          <BatchDeleteButton
            v-model:active="deleteMode"
            :ids="selected.map((r: any) => String(r.contractNo || r.contractId))"
            :summary="`已选择 ${selected.length} 份合同：${selected.map((r: any) => r.contractNo || r.contractId).join('、')}`"
            :delete-fn="(ids, config) => unwrap(financeApi.batchDeleteContracts(ids, config))"
            @done="onDeleted"
          />
        </template>
        <template #toolbar>
          <el-input
            v-model="keyword"
            placeholder="合同 ID / 合同号 / 客户 ID"
            clearable
            style="width: 240px"
            @keyup.enter="onSearch"
          />
          <el-select v-model="dueState" placeholder="到期状态" clearable style="width: 150px">
            <el-option label="已到期" value="EXPIRED" />
            <el-option label="30 天内到期" value="SOON" />
            <el-option label="正常" value="NORMAL" />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </template>
        <template #col-status="{ row }"><StatusTag :status="row.status" /></template>
        <template #col-remaining="{ row }">
          <span :class="{ overdue: row.remaining?.overdue }" :title="row.remaining?.hint">
            {{ row.remaining?.text ?? '--' }}
          </span>
        </template>
      </SentTable>
    </div>
  </div>
</template>

<script setup lang="ts">
/** 合同管理（P6-47）：列表走后端真实分页；到期天数为展示层计算，不写库、不臆造提醒记录。 */
import { ref } from 'vue';
import { unwrap } from '@/utils/http';
import { financeApi } from '@/api';
import { fmtDate, fmtMoney } from '@sent/shared';
import { useTable } from '@/composables/useTable';
import { useBatchDelete } from '@/composables/useBatchDelete';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import BatchDeleteButton from '@/components/BatchDeleteButton.vue';
import type { TableColumn } from '@/components/types';

const columns: TableColumn[] = [
  { prop: 'contractId', label: '合同 ID', width: 130 },
  { prop: 'contractNo', label: '合同号', minWidth: 180 },
  { prop: 'customerId', label: '客户 ID', width: 130 },
  { prop: 'amountCent', label: '合同金额', width: 140, align: 'right',
    formatter: (_r, v) => fmtMoney(v) },
  { prop: 'status', label: '状态', width: 120, align: 'center' },
  { prop: 'validToMillis', label: '到期日', width: 130, formatter: (_r, v) => fmtDate(v) },
  { prop: 'remaining', label: '剩余天数', width: 130, align: 'center' },
];

const dueState = ref<string>('');
/** 合同 ID / 合同号 / 客户 ID 三选一模糊搜索，条件由后端 SQL 收敛 */
const keyword = ref('');

interface RemainingInfo {
  days: number | null;
  text: string;
  overdue: boolean;
  hint: string;
}

/**
 * 到期日被 Jackson 序列化成「毫秒时间戳字符串」（后端 Long → String），
 * 只认 number 会让「剩余天数」整列退化成 `--`，此处先归一成数字。
 */
function toMillis(value: number | string | null | undefined): number | null {
  const n = typeof value === 'string' ? Number(value) : value;
  if (n === null || n === undefined || !Number.isFinite(n) || n <= 0) return null;
  return n;
}

/** 按「自然日」比较，避免当前钟点让「明天到期」被算成 1 天或 2 天 */
function dayStart(ms: number): number {
  const date = new Date(ms);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}

/**
 * 剩余天数与真实系统时间比较（Date.now()），不用任何写死的基准日期。
 * < 0 已逾期 / = 0 今日到期 / > 0 剩余。30 天内到期用醒目色，与后端 dueState=SOON 口径一致。
 */
function remainingInfo(validTo: number | string | null | undefined): RemainingInfo {
  const ms = toMillis(validTo);
  if (ms === null) return { days: null, text: '--', overdue: false, hint: '未设置到期日' };
  const days = Math.round((dayStart(ms) - dayStart(Date.now())) / 86_400_000);
  const hint = `到期日 ${fmtDate(ms)}（${days < 0 ? '已逾期' : '剩余'} ${Math.abs(days)} 天）`;
  if (days < 0) return { days, text: `已逾期 ${Math.abs(days)} 天`, overdue: true, hint };
  if (days === 0) return { days, text: '今日到期', overdue: true, hint };
  return { days, text: `剩余 ${days} 天`, overdue: days <= 30, hint };
}

const { rows, total, loading, page, size, load, onPage, onSize, onSearch } = useTable<any>({
  fetch: (params) => unwrap(financeApi.contracts({
    ...params,
    dueState: dueState.value || undefined,
    keyword: keyword.value.trim() || undefined,
  })),
  // 到期状态由后端按 expire_date 在 SQL 里判定；前端再过滤会让「本页之外」的合同被丢掉。
  // 剩余天数是展示层派生值，随每次加载以真实当前时间重算。
  transform: ({ list, total: count }) => ({
    total: count,
    list: list.map((row: any) => ({ ...row, remaining: remainingInfo(row.validToMillis) })),
  }),
  errorMessage: '',
});
const { deleteMode, selected, onDeleted } = useBatchDelete(load);
</script>
<style scoped>
.overdue { color: #d03050; font-weight: 600; }
</style>
