<template>
  <div>
    <div class="sent-bar"><strong>我的订单</strong></div>

    <el-empty v-if="!loading && orders.length === 0" description="暂无订单" />

    <div v-for="o in orders" :key="o.orderNo" class="sent-card order-card" @click="goDetail(o.orderNo)">
      <div class="order-card__head">
        <span class="order-card__no">{{ o.orderNo }}</span>
        <el-tag :type="tone(o.status)" size="small">{{ label(o.status) }}</el-tag>
      </div>
      <div class="order-card__foot">
        <span class="sent-price">应付 ¥{{ o.payableYuan || '0.00' }}</span>
      </div>
      <div class="order-card__ops" @click.stop>
        <el-button
          v-if="o.status === 'WAIT_PAY'"
          type="primary" size="small"
          :loading="acting === o.orderNo"
          @click="pay(o)"
        >去支付</el-button>
        <el-button
          v-if="o.status === 'WAIT_PAY'"
          size="small"
          :loading="acting === o.orderNo"
          @click="cancel(o)"
        >取消</el-button>
        <el-button
          v-if="o.status === 'WAIT_RECEIVE'"
          type="success" size="small"
          :loading="acting === o.orderNo"
          @click="confirm(o)"
        >确认收货</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { orderApi, type OrderListItem } from '@/api/order';
import { paymentApi, scheduleMockPayCallback } from '@/api/payment';
import { statusLabel, statusTone, type StatusTone } from '@sent/shared';

const router = useRouter();
const orders = ref<OrderListItem[]>([]);
const loading = ref(false);
const acting = ref('');

function label(s: string | null): string { return statusLabel(s); }
function tone(s: string | null): StatusTone { return statusTone(s); }
function goDetail(no: string): void { router.push(`/order/${no}`); }

async function pay(o: OrderListItem): Promise<void> {
  acting.value = o.orderNo;
  try {
    const pay = await unwrap(paymentApi.create({ orderNo: o.orderNo, amountCent: o.payableCent ?? 0, channel: 'BALANCE', idemKey: `pay_${o.orderNo}` }));
    scheduleMockPayCallback({
      txnNo: pay.txnNo ?? '',
      orderNo: o.orderNo,
      amountCent: Number(pay.amountCent ?? o.payableCent ?? 0),
      channel: 'BALANCE',
      payChannelTradeNo: `MOCK-${o.orderNo}-${Date.now()}`,
      paidAtMillis: Date.now(),
    });
    ElMessage.success('支付成功');
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '支付失败');
  } finally {
    acting.value = '';
  }
}

async function cancel(o: OrderListItem): Promise<void> {
  acting.value = o.orderNo;
  try {
    await unwrap(orderApi.cancel(o.orderNo));
    ElMessage.success('已取消');
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '取消失败');
  } finally {
    acting.value = '';
  }
}

async function confirm(o: OrderListItem): Promise<void> {
  acting.value = o.orderNo;
  try {
    await unwrap(orderApi.confirm(o.orderNo, []));
    ElMessage.success('已确认收货');
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '操作失败');
  } finally {
    acting.value = '';
  }
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const res = await unwrap(orderApi.list({ page: 1, size: 50 }));
    orders.value = res.list ?? [];
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.order-card {
  cursor: pointer;
}
.order-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.order-card__no {
  font-size: 13px;
  color: #555;
}
.order-card__foot {
  font-size: 14px;
  margin-bottom: 8px;
}
.order-card__ops {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
