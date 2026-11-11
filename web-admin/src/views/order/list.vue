<template>
  <div>
    <h2 class="sent-page-title">订单列表</h2>
    <div class="sent-panel">
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        exportable
        export-name="订单列表"
        :operation-width="200"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar>
          <el-input
            v-model="query.keyword"
            placeholder="订单号"
            clearable
            style="width: 220px"
            @keyup.enter="onSearch"
          />
          <el-select v-model="query.status" placeholder="订单状态" clearable style="width: 160px">
            <el-option
              v-for="s in orderStatuses"
              :key="s"
              :label="statusLabel(s)"
              :value="s"
            />
          </el-select>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="router.push('/order/shipment')">发货工作台</el-button>
        </template>

        <template #col-status="{ row }"><StatusTag :status="row.status" /></template>
        <template #col-payStatus="{ row }"><StatusTag :status="row.payStatus" /></template>

        <template #operations="{ row }">
          <el-button link type="primary" @click="router.push(`/order/detail/${row.orderNo}`)">
            详情
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="isClosed(row.status)"
            @click="closeOrder(row)"
          >
            关闭
          </el-button>
        </template>
      </SentTable>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 订单列表（P6-33）：筛选/分页/导出复用 SentTable，状态统一 StatusTag。
 * 关单属危险操作：先填原因 → openConfirm 换一次性令牌 → 带 X-Confirm-Token 提交。
 */
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { unwrap } from '@/utils/http';
import { orderApi } from '@/api';
import { OrderStatus, fmtCent, fmtDateTime, fmtMoney, statusLabel } from '@sent/shared';
import { openConfirm } from '@/utils/confirm';
import { usePagination } from '@/composables/usePagination';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import type { TableColumn } from '@/components/types';

const router = useRouter();
const orderStatuses = Object.values(OrderStatus);

const columns: TableColumn[] = [
  { prop: 'orderNo', label: '订单号', minWidth: 190 },
  { prop: 'status', label: '订单状态', width: 120, align: 'center' },
  { prop: 'payStatus', label: '支付状态', width: 120, align: 'center' },
  { prop: 'payableCent', label: '应付金额', width: 140, align: 'right',
    formatter: (_row, value) => fmtMoney(value) },
  { prop: 'goodsAmountCent', label: '货款', width: 130, align: 'right',
    formatter: (_row, value) => fmtCent(value) },
  { prop: 'createdAt', label: '下单时间', width: 160,
    formatter: (_row, value) => fmtDateTime(value) },
];

const rows = ref<any[]>([]);
const total = ref(0);
const { page, size } = usePagination();
const loading = ref(false);
const query = reactive<{ keyword: string; status?: string }>({ keyword: '' });

function isClosed(status: string): boolean {
  return status === OrderStatus.CLOSED
    || status === OrderStatus.CANCELLED
    || status === OrderStatus.COMPLETED;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await unwrap(orderApi.list({
      page: page.value,
      size: size.value,
      keyword: query.keyword || undefined,
      status: query.status || undefined,
    }));
    rows.value = data?.list ?? [];
    total.value = data?.total ?? 0;
  } catch (error) {
    rows.value = [];
    total.value = 0;
    ElMessage.error((error as Error)?.message || '订单列表加载失败');
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

async function closeOrder(row: any): Promise<void> {
  if (isClosed(row.status)) {
    ElMessage.warning('当前状态不允许关单');
    return;
  }
  let reason = '';
  try {
    const { value } = await ElMessageBox.prompt('请填写关单原因', '关闭订单', {
      inputPlaceholder: '如：客户取消 / 缺货',
      inputValidator: (v: string) => (v && v.trim() ? true : '原因不能为空'),
      type: 'warning',
    });
    reason = String(value).trim();
  } catch {
    return;
  }

  try {
    const grant = await openConfirm({
      action: 'ORDER_CLOSE',
      targetId: String(row.orderNo),
      summary: `订单 ${row.orderNo} · 应付 ${fmtMoney(row.payableCent)} · 原因 ${reason}`,
    });
    if (grant.isExpired()) {
      ElMessage.warning('确认已超时，请重新确认');
      return;
    }
    await unwrap(orderApi.close(String(row.orderNo), reason, { headers: grant.headers }));
    ElMessage.success('订单已关闭');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '关单失败');
  }
}

onMounted(() => { void load(); });
</script>
