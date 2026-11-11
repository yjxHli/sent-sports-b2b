<template>
  <div>
    <div class="sent-bar">
      <el-button text @click="goBack">‹ 返回</el-button>
      <strong>订单详情</strong>
    </div>

    <div v-if="loading" class="detail-hint">加载中…</div>
    <el-empty v-else-if="!order" description="订单不存在" />

    <template v-else>
      <div class="sent-card">
        <div class="row"><span>订单号</span><span>{{ order.orderNo }}</span></div>
        <div class="row"><span>主状态</span><el-tag :type="tone(order.status)" size="small">{{ label(order.status) }}</el-tag></div>
        <div class="row"><span>支付状态</span><span>{{ label(order.payStatus) }}</span></div>
        <div class="row"><span>发货状态</span><span>{{ label(order.shipStatus) }}</span></div>
        <div class="row"><span>退款状态</span><span>{{ label(order.refundStatus) }}</span></div>
      </div>

      <div class="sent-card">
        <div class="sent-title">金额</div>
        <div class="row"><span>商品金额</span><span>¥{{ order.goodsAmountYuan || '0.00' }}</span></div>
        <div class="row"><span>运费</span><span>¥{{ order.freightYuan || '0.00' }}</span></div>
        <div class="row" v-if="order.pointDeductCent"><span>积分抵扣</span><span>-¥{{ order.pointDeductYuan }}</span></div>
        <div class="row row--total"><span>应付</span><span class="sent-price">¥{{ order.payableYuan || '0.00' }}</span></div>
      </div>

      <div class="detail-ops">
        <el-button
          v-if="order.status === 'WAIT_PAY'"
          type="primary" :loading="acting"
          @click="pay"
        >去支付</el-button>
        <el-button
          v-if="order.status === 'WAIT_PAY'"
          :loading="acting"
          @click="cancel"
        >取消订单</el-button>
        <el-button
          v-if="order.status === 'WAIT_RECEIVE'"
          type="success" :loading="acting"
          @click="confirm"
        >确认收货</el-button>
        <el-button
          v-if="canRefund"
          @click="openRefund"
        >申请退款</el-button>
      </div>
    </template>

    <el-dialog v-model="refundVisible" title="申请退款" width="85%">
      <el-input v-model="refundReason" type="textarea" :rows="3" placeholder="退款原因" />
      <el-input v-model="refundProof" placeholder="退款凭证（演示填任意文本）" style="margin-top: 10px" />
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="submitRefund">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { orderApi, type OrderVO } from '@/api/order';
import { paymentApi } from '@/api/payment';
import { refundApi } from '@/api/refund';
import { statusLabel, statusTone, type StatusTone } from '@sent/shared';

const route = useRoute();
const router = useRouter();

const order = ref<OrderVO | null>(null);
const loading = ref(false);
const acting = ref(false);

const refundVisible = ref(false);
const refundReason = ref('');
const refundProof = ref('demo');

const canRefund = computed(() => {
  const s = order.value?.status;
  return s === 'WAIT_SHIP' || s === 'WAIT_RECEIVE' || s === 'PART_SHIPPED' || s === 'PAID';
});

function label(s: string | null | undefined): string { return statusLabel(s); }
function tone(s: string | null | undefined): StatusTone { return statusTone(s); }
function goBack(): void { router.back(); }

async function pay(): Promise<void> {
  if (!order.value) return;
  acting.value = true;
  try {
    await unwrap(paymentApi.create({
      orderNo: order.value.orderNo,
      amountCent: order.value.payableCent ?? 0,
      channel: 'BALANCE',
      idemKey: `pay_${order.value.orderNo}`,
    }));
    ElMessage.success('支付成功');
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '支付失败');
  } finally {
    acting.value = false;
  }
}

async function cancel(): Promise<void> {
  if (!order.value) return;
  acting.value = true;
  try {
    await unwrap(orderApi.cancel(order.value.orderNo));
    ElMessage.success('已取消');
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '取消失败');
  } finally {
    acting.value = false;
  }
}

async function confirm(): Promise<void> {
  if (!order.value) return;
  acting.value = true;
  try {
    await unwrap(orderApi.confirm(order.value.orderNo, []));
    ElMessage.success('已确认收货');
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '操作失败');
  } finally {
    acting.value = false;
  }
}

function openRefund(): void { refundVisible.value = true; }

async function submitRefund(): Promise<void> {
  if (!order.value || !order.value.payableCent) return;
  acting.value = true;
  try {
    await unwrap(refundApi.apply({
      orderNo: order.value.orderNo,
      lines: [{ skuId: 0, amountCent: order.value.payableCent ?? 0 }],
      reason: refundReason.value || '申请退款',
      proof: refundProof.value,
    }));
    ElMessage.success('退款申请已提交');
    refundVisible.value = false;
  } catch (e) {
    ElMessage.error((e as Error).message || '申请失败');
  } finally {
    acting.value = false;
  }
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    order.value = await unwrap(orderApi.detail(String(route.params.orderNo)));
  } catch {
    order.value = null;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.detail-hint {
  text-align: center;
  padding: 40px 0;
  color: #b5b9c0;
}
.row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #555;
  padding: 4px 0;
}
.row--total {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
  border-top: 1px dashed #eee;
  margin-top: 4px;
  padding-top: 8px;
}
.detail-ops {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px;
}
</style>
