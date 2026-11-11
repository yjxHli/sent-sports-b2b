<template>
  <div>
    <h2 class="sent-page-title">退款审核</h2>
    <div class="sent-panel">
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        exportable
        export-name="退款单"
        :operation-width="170"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar>
          <el-select v-model="status" placeholder="退款状态" clearable style="width: 150px">
            <el-option
              v-for="s in Object.values(RefundStatus)"
              :key="s"
              :label="statusLabel(s)"
              :value="s"
            />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </template>

        <template #col-status="{ row }"><StatusTag :status="row.status" /></template>

        <template #operations="{ row }">
          <el-button
            link
            type="success"
            :disabled="row.status !== RefundStatus.PENDING"
            @click="approve(row)"
          >
            通过
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="row.status !== RefundStatus.PENDING"
            @click="reject(row)"
          >
            驳回
          </el-button>
        </template>
      </SentTable>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 退款审核（P6-43）：列表 / 审核均走管理端真实端点（/admin/refunds）；
 * 审核通过是危险操作，必须 openConfirm('REFUND_APPROVE') 换 X-Confirm-Token 并在提交前复查过期。
 */
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { unwrap } from '@/utils/http';
import { financeApi } from '@/api';
import { RefundStatus, fmtDateTime, fmtMoney, statusLabel } from '@sent/shared';
import { openConfirm } from '@/utils/confirm';
import { usePagination } from '@/composables/usePagination';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

const columns: TableColumn[] = [
  { prop: 'refundNo', label: '退款单号', minWidth: 190 },
  { prop: 'orderNo', label: '订单号', minWidth: 190 },
  { prop: 'status', label: '状态', width: 120, align: 'center' },
  { prop: 'amountCent', label: '退款金额', width: 140, align: 'right',
    formatter: (_r, v) => fmtMoney(v) },
  { prop: 'reason', label: '退款原因', minWidth: 160 },
  { prop: 'createdAt', label: '申请时间', width: 160, formatter: (_r, v) => fmtDateTime(v) },
];

const rows = ref<any[]>([]);
const total = ref(0);
const { page, size } = usePagination();
const loading = ref(false);
const status = ref<string>('');

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await unwrap(financeApi.refunds({
      page: page.value, size: size.value, status: status.value || undefined,
    }));
    rows.value = data?.list ?? [];
    total.value = data?.total ?? 0;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    ElMessage.error((error as Error)?.message || '退款单加载失败');
  } finally {
    loading.value = false;
  }
}

function onSearch(): void { page.value = 1; void load(); }
function onPage(value: number): void { page.value = value; void load(); }
function onSize(value: number): void { size.value = value; page.value = 1; void load(); }

async function approve(row: any): Promise<void> {
  try {
    const grant = await openConfirm({
      action: 'REFUND_APPROVE',
      targetId: String(row.refundNo),
      summary: `退款单 ${row.refundNo} · 订单 ${row.orderNo} · 金额 ${fmtMoney(row.amountCent)}`,
    });
    if (grant.isExpired()) {
      ElMessage.warning('确认已超时，请重新确认');
      return;
    }
    await unwrap(financeApi.auditRefund(String(row.refundNo), { approved: true, remark: '审核通过' }, grant.token));
    ElMessage.success('退款审核已通过');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '退款审核提交失败');
  }
}

async function reject(row: any): Promise<void> {
  let remark = '';
  try {
    const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回退款申请', {
      inputValidator: (v: string) => (v && v.trim() ? true : '原因不能为空'),
      type: 'warning',
    });
    remark = String(value).trim();
  } catch {
    return;
  }
  try {
    await unwrap(financeApi.auditRefund(String(row.refundNo), { approved: false, remark }));
    ElMessage.success('已驳回');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '退款审核提交失败');
  }
}

onMounted(() => { void load(); });
</script>
