<template>
  <div>
    <h2 class="sent-page-title">订单详情</h2>

    <div class="sent-panel">
      <div class="sent-form-row">
        <el-button link type="primary" @click="router.push('/order/list')">返回列表</el-button>
        <el-button link @click="router.push('/order/shipment')">去发货工作台</el-button>
        <span class="order-no">{{ orderNo }}</span>
      </div>

      <el-descriptions :column="3" border v-loading="loading">
        <el-descriptions-item label="订单状态">
          <StatusTag :status="detail?.status" />
        </el-descriptions-item>
        <el-descriptions-item label="支付状态">
          <StatusTag :status="detail?.payStatus" />
        </el-descriptions-item>
        <el-descriptions-item label="发货状态">
          <StatusTag :status="detail?.shipStatus" />
        </el-descriptions-item>
        <el-descriptions-item label="客户 ID">{{ detail?.customerId ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ fmtDateTime(detail?.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="售后状态">
          <StatusTag :status="detail?.refundStatus" />
        </el-descriptions-item>
      </el-descriptions>

      <h3 class="section-title">金额拆分</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="货款">{{ fmtMoney(detail?.goodsAmountCent) }}</el-descriptions-item>
        <el-descriptions-item label="运费">{{ fmtMoney(detail?.freightCent) }}</el-descriptions-item>
        <el-descriptions-item label="积分抵扣">{{ fmtMoney(detail?.pointDeductCent) }}</el-descriptions-item>
        <el-descriptions-item label="应付金额">
          <strong>{{ fmtMoney(detail?.payableCent) }}</strong>
        </el-descriptions-item>
      </el-descriptions>

      <h3 class="section-title">订单进度</h3>
      <el-alert
        v-if="terminated"
        :title="`订单已终止：${statusLabel(detail?.status)}`"
        type="info"
        :closable="false"
        show-icon
      />
      <el-steps v-else :active="activeStep" finish-status="success" align-center>
        <el-step v-for="step in steps" :key="step" :title="statusLabel(step)" />
      </el-steps>

      <h3 class="section-title">订单行明细</h3>
      <el-table v-if="lines.length" :data="lines" border size="small">
        <el-table-column prop="skuName" label="商品 / SKU" min-width="180" />
        <el-table-column prop="spec" label="规格" width="140" />
        <el-table-column prop="qty" label="数量" width="90" align="center" />
        <el-table-column label="单价" width="130" align="right">
          <template #default="scope">{{ fmtMoney(scope.row.unitPriceCent) }}</template>
        </el-table-column>
        <el-table-column label="已发数量" width="100" align="center">
          <template #default="scope">{{ scope.row.shippedQty ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="定制状态" width="130" align="center">
          <template #default="scope"><StatusTag :status="scope.row.customStatus" /></template>
        </el-table-column>
      </el-table>
      <el-empty
        v-else
        description="该订单暂无商品行"
        :image-size="60"
      />

      <div class="sent-form-row detail-actions">
        <el-tooltip
          v-if="!canClose"
          content="仅「待发货 / 部分发货」可关单；已全部发货请走退货流程"
          placement="top"
        >
          <span class="close-btn-wrap">
            <el-button type="danger" disabled>关闭订单</el-button>
          </span>
        </el-tooltip>
        <el-button v-else type="danger" :loading="closing" @click="closeOrder">关闭订单</el-button>
        <el-button @click="load">刷新</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 订单详情（P6-34）。后端 GET /api/admin/orders/{orderNo} 返回订单头 + 金额 + 订单行。
 * 关单流程：原因弹窗（必填）→ utils/confirm.ts 的 openConfirm 换一次性令牌（短语「确认关闭」）
 * → 携带 X-Confirm-Token 提交 → 成功后刷新详情。后端按状态机仅允许待发货/部分发货关单。
 * 状态文案与颜色一律走 StatusTag / STATUS_LABELS，金额/时间走 fmtMoney / fmtDateTime。
 */
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { unwrap } from '@/utils/http';
import { orderApi } from '@/api';
import type { AdminOrderDetailVO, AdminOrderLineVO } from '@sent/shared';
import { OrderStatus, fmtDateTime, fmtMoney, statusLabel } from '@sent/shared';
import { openConfirm } from '@/utils/confirm';
import StatusTag from '@/components/StatusTag.vue';

const route = useRoute();
const router = useRouter();
const orderNo = String(route.params.orderNo ?? '');

const detail = ref<AdminOrderDetailVO | null>(null);
const loading = ref(false);
const closing = ref(false);

/** 与后端 OrderStateMachine.ADMIN_CLOSE 同源：只有整单未发 / 部分发货可关单。 */
const CLOSEABLE_STATUSES: string[] = [OrderStatus.WAIT_SHIP, OrderStatus.PART_SHIPPED];
const canClose = computed(() => CLOSEABLE_STATUSES.includes(detail.value?.status ?? ''));

const steps = [
  OrderStatus.WAIT_PAY,
  OrderStatus.WAIT_SHIP,
  OrderStatus.WAIT_RECEIVE,
  OrderStatus.COMPLETED,
];

const STEP_INDEX: Record<string, number> = {
  WAIT_PAY: 0,
  WAIT_SHIP: 1,
  PART_SHIPPED: 1,
  WAIT_RECEIVE: 2,
  COMPLETED: 3,
};

const activeStep = computed(() => STEP_INDEX[detail.value?.status ?? ''] ?? 0);
const terminated = computed(() =>
  detail.value?.status === OrderStatus.CANCELLED
  || detail.value?.status === OrderStatus.CLOSED,
);
const lines = computed<AdminOrderLineVO[]>(() =>
  Array.isArray(detail.value?.lines) ? detail.value.lines : [],
);

async function load(): Promise<void> {
  if (!orderNo) return;
  loading.value = true;
  try {
    detail.value = await unwrap(orderApi.detail(orderNo));
  } catch (error) {
    detail.value = null;
    ElMessage.error((error as Error)?.message || '订单详情加载失败');
  } finally {
    loading.value = false;
  }
}

async function closeOrder(): Promise<void> {
  if (!canClose.value || closing.value) return;
  let reason = '';
  try {
    // 项目使用 Element Plus 而非 pure-admin 的 ReDialog，这里用等价的 prompt 弹窗
    // 收集关闭原因：必填、去空白、取消即中止，不发起任何请求。
    const { value } = await ElMessageBox.prompt(
      `关单将全额退款 ${fmtMoney(detail.value?.paidCent ?? detail.value?.payableCent)}` +
      '，并释放未发货部分的预占库存；操作不可撤销。',
      '关闭订单',
      {
        inputType: 'textarea',
        inputPlaceholder: '请填写关闭原因，如：客户取消 / 缺货',
        inputValidator: (v: string) => (v && v.trim() ? true : '关闭原因不能为空'),
        inputErrorMessage: '关闭原因不能为空',
        confirmButtonText: '下一步',
        cancelButtonText: '取消',
        type: 'warning',
      },
    );
    reason = String(value).trim();
  } catch {
    return;
  }
  closing.value = true;
  try {
    // 先换一次性确认令牌（openConfirm 会要求准确输入「确认关闭」），再携带令牌提交。
    const grant = await openConfirm({
      action: 'ORDER_CLOSE',
      targetId: orderNo,
      summary: `订单 ${orderNo} · 应付 ${fmtMoney(detail.value?.payableCent)} · 原因 ${reason}`,
    });
    if (grant.isExpired()) {
      ElMessage.warning('确认已超时，请重新确认');
      return;
    }
    await unwrap(orderApi.close(orderNo, reason, { headers: grant.headers }));
    ElMessage.success('订单已关闭，全额退款与库存补偿已完成');
    await load();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '关单失败');
  } finally {
    closing.value = false;
  }
}

onMounted(() => { void load(); });
</script>

<style scoped>
.order-no { margin-left: auto; font-weight: 600; color: #12664f; }
.section-title { margin: 22px 0 12px; font-size: 15px; color: #33463f; }
.detail-actions { margin-top: 22px; }
/* 禁用按钮不触发 mouse 事件，用外层 span 承载 tooltip */
.close-btn-wrap { display: inline-flex; margin-right: 12px; }
</style>
