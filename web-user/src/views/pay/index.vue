<template>
  <div class="pay-page">
    <div class="sent-bar"><strong>订单支付</strong></div>

    <div v-if="loading" class="sent-card pay-loading">订单信息加载中…</div>

    <div v-else-if="!order" class="sent-card pay-loading">
      <p>找不到该订单或无权查看</p>
      <el-button type="primary" @click="goOrders">返回我的订单</el-button>
    </div>

    <!-- 支付成功：确认支付后才出现，进入支付页时绝不提前弹 -->
    <div v-else-if="paid" class="sent-card pay-success" data-testid="pay-success">
      <div class="pay-success__icon">✓</div>
      <h3>支付成功</h3>
      <p>订单号：<span data-testid="pay-order-no">{{ order.orderNo }}</span></p>
      <p>实付金额：<b class="sent-price">¥{{ paidAmount }}</b></p>
      <div class="pay-success__ops">
        <el-button @click="goHome">返回首页</el-button>
        <el-button type="primary" @click="goDetail">查看订单</el-button>
      </div>
    </div>

    <!-- 非待付款订单：不展示支付按钮，避免对已支付订单重复扣款 -->
    <div v-else-if="order.status !== 'WAIT_PAY'" class="sent-card pay-loading">
      <p>该订单当前状态为「{{ statusText(order.status) }}」，无需支付</p>
      <el-button type="primary" @click="goDetail">查看订单</el-button>
    </div>

    <template v-else>
      <div class="sent-card pay-amount-card">
        <div class="pay-amount__label">应付金额</div>
        <div class="pay-amount__value sent-price" data-testid="pay-amount">¥{{ order.payableYuan }}</div>
        <div v-if="countdown" class="pay-amount__countdown" data-testid="pay-countdown">
          {{ expired ? '支付时间已结束，订单将被自动取消' : `请在 ${countdown} 内完成支付` }}
        </div>
      </div>

      <div class="sent-card pay-method-card">
        <div class="sent-title">支付方式</div>
        <div class="pay-method">
          <span class="pay-method__mark">余</span>
          <div class="pay-method__main">
            <b>余额支付</b>
            <span>演示环境使用模拟渠道，确认后自动回写支付结果</span>
          </div>
          <span class="pay-method__checked">✓</span>
        </div>
      </div>

      <div class="sent-card pay-order-card">
        <div class="sent-title">订单信息</div>
        <div class="pay-order__row">
          <span>订单号</span><span data-testid="pay-order-no">{{ order.orderNo }}</span>
        </div>
        <div class="pay-order__row">
          <span>商品金额</span><span>¥{{ order.goodsAmountYuan }}</span>
        </div>
        <div v-if="Number(order.customFeeCent ?? 0) > 0" class="pay-order__row">
          <span>定制费</span><span>¥{{ (Number(order.customFeeCent) / 100).toFixed(2) }}</span>
        </div>
        <div class="pay-order__row"><span>运费</span><span>¥{{ order.freightYuan }}</span></div>
        <div v-if="Number(order.discountCent ?? 0) > 0" class="pay-order__row">
          <span>优惠抵扣</span><span class="sent-price">-¥{{ (Number(order.discountCent) / 100).toFixed(2) }}</span>
        </div>
        <div v-if="Number(order.pointDeductCent ?? 0) > 0" class="pay-order__row">
          <span>积分抵扣</span><span class="sent-price">-¥{{ order.pointDeductYuan }}</span>
        </div>
        <div v-for="it in order.items ?? []" :key="String(it.skuId)" class="pay-order__item">
          <span>{{ it.title || '订单商品' }}</span><span>×{{ it.qty ?? 0 }}</span>
        </div>
      </div>

      <div class="pay-submit-row">
        <el-button
          type="primary"
          class="pay-submit"
          :loading="paying"
          :disabled="expired"
          data-testid="pay-submit"
          @click="confirmPay"
        >确认支付 ¥{{ order.payableYuan }}</el-button>
      </div>
      <p class="pay-hint">点击确认后才会真正发起支付，支付成功后订单进入待发货</p>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 独立订单支付页（2026-11-18 用户反馈）：
 * 「暂不支付」生成的 WAIT_PAY 订单，从订单列表 / 详情点「去支付」必须先进入本页，
 * 由用户确认金额与支付方式后再真正调用 /payments。进页面就弹「支付成功」是错误时序。
 */
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { statusLabel } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { createIdempotencyScope } from '@/utils/idempotent';
import { orderApi, type OrderVO } from '@/api/order';
import { paymentApi, scheduleMockPayCallback } from '@/api/payment';

const route = useRoute();
const router = useRouter();
const orderNo = String(route.params.orderNo ?? '');
const payIdem = createIdempotencyScope('user-pay');

const order = ref<OrderVO | null>(null);
const loading = ref(true);
const paying = ref(false);
const paid = ref(false);
const paidAmount = ref('');
const now = ref(Date.now());
let ticker: number | undefined;

const remainingMillis = computed(() => {
  const end = Number(order.value?.expireAtMillis ?? 0);
  return end > 0 ? Math.max(0, end - now.value) : 0;
});
const expired = computed(() => remainingMillis.value <= 0);
const countdown = computed(() => {
  const end = Number(order.value?.expireAtMillis ?? 0);
  if (!end || order.value?.status !== 'WAIT_PAY') return '';
  const total = Math.floor(remainingMillis.value / 1000);
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`;
});

function statusText(status: string | null): string {
  return status ? statusLabel(status) : '未知';
}
function goDetail(): void { void router.push(`/order/${orderNo}`); }
function goOrders(): void { void router.push('/orders'); }
function goHome(): void { void router.push('/home'); }

async function load(): Promise<void> {
  loading.value = true;
  try {
    order.value = await unwrap(orderApi.detail(orderNo));
  } catch {
    order.value = null;
  } finally {
    loading.value = false;
  }
}

/** 只有点「确认支付」才走到这里；幂等键与结算页即时支付隔离，防重复扣款 */
async function confirmPay(): Promise<void> {
  const current = order.value;
  if (!current || current.status !== 'WAIT_PAY' || paying.value) return;
  paying.value = true;
  payIdem.getOrCreate();
  try {
    const pay = await unwrap(paymentApi.create({
      orderNo: current.orderNo,
      amountCent: Number(current.payableCent ?? 0),
      channel: 'BALANCE',
      idemKey: payIdem.current() ?? '',
    }, { headers: payIdem.headers() }));
    payIdem.clear();
    scheduleMockPayCallback({
      txnNo: pay.txnNo ?? '',
      orderNo: current.orderNo,
      amountCent: Number(pay.amountCent ?? current.payableCent ?? 0),
      channel: 'BALANCE',
      payChannelTradeNo: `MOCK-${current.orderNo}-${Date.now()}`,
      paidAtMillis: Date.now(),
    });
    paidAmount.value = pay.amountYuan || current.payableYuan || '0.00';
    paid.value = true;
  } catch (e) {
    ElMessage.error((e as Error).message || '支付失败');
  } finally {
    paying.value = false;
  }
}

onMounted(() => {
  void load();
  ticker = window.setInterval(() => { now.value = Date.now(); }, 1000);
});
onUnmounted(() => {
  if (ticker !== undefined) window.clearInterval(ticker);
});
</script>

<style scoped>
.pay-page { width: var(--sent-container); max-width: calc(100% - 32px); margin: 0 auto; padding-bottom: 40px; }
.pay-loading { margin-top: 16px; padding: 32px 16px; text-align: center; color: var(--sent-muted); font-size: 13px; }
.pay-amount-card { margin-top: 14px; padding: 28px 16px; text-align: center; }
.pay-amount__label { font-size: 13px; color: var(--sent-muted); }
.pay-amount__value { margin-top: 8px; font-size: 34px; font-weight: 700; }
.pay-amount__countdown { margin-top: 8px; font-size: 12px; color: var(--sent-accent-dark); }
.pay-method-card, .pay-order-card { margin-top: 12px; }
.pay-method { display: flex; align-items: center; gap: 12px; padding: 14px 0 2px; }
.pay-method__mark {
  width: 34px; height: 34px; flex: none; border-radius: 50%;
  background: var(--sent-primary); color: #fff; font-size: 14px;
  display: flex; align-items: center; justify-content: center;
}
.pay-method__main { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.pay-method__main b { font-size: 14px; color: var(--sent-text); }
.pay-method__main span { font-size: 11px; color: var(--sent-muted); }
.pay-method__checked { color: var(--sent-primary); font-weight: 700; }
.pay-order__row { display: flex; justify-content: space-between; padding: 5px 0; font-size: 13px; color: var(--sent-text-2); }
.pay-order__item { display: flex; justify-content: space-between; padding: 5px 0; font-size: 12px; color: var(--sent-muted); }
.pay-submit-row { margin-top: 16px; }
.pay-submit { width: 100%; margin: 0; }
.pay-hint { margin: 10px 0 0; text-align: center; font-size: 11px; color: var(--sent-muted); }
.pay-success { margin-top: 16px; padding: 36px 16px; text-align: center; }
.pay-success__icon {
  width: 52px; height: 52px; margin: 0 auto 12px; border-radius: 50%;
  background: var(--sent-primary); color: #fff; font-size: 28px;
  display: flex; align-items: center; justify-content: center;
}
.pay-success h3 { margin: 0 0 12px; font-size: 20px; color: var(--sent-text); }
.pay-success p { margin: 6px 0; font-size: 13px; color: var(--sent-text-2); }
.pay-success__ops { margin-top: 22px; display: flex; gap: 12px; justify-content: center; }
</style>
