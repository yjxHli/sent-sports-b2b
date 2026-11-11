<template>
  <div>
    <div class="sent-bar"><strong>确认订单</strong></div>

    <el-empty v-if="items.length === 0" description="没有可结算的商品">
      <el-button type="primary" @click="goProducts">去选购</el-button>
    </el-empty>

    <template v-else>
      <div class="sent-card">
        <div class="sent-title">收货信息</div>
        <el-input v-model="contact" placeholder="收货人 / 联系电话（演示）" />
      </div>

      <div class="sent-card">
        <div class="sent-title">商品清单</div>
        <div v-for="it in items" :key="it.skuId" class="co-line">
          <span>{{ it.name }}</span>
          <span>×{{ it.qty }}</span>
        </div>
        <el-checkbox v-model="customFlag" class="co-custom">
          含 UV 定制件（下单后需上传图稿并经客服审图）
        </el-checkbox>
      </div>

      <div v-if="quote" class="sent-card">
        <div class="quote-line"><span>商品金额</span><span>¥{{ quote.goodsAmountYuan }}</span></div>
        <div class="quote-line"><span>运费</span><span>¥{{ quote.freightYuan }}</span></div>
        <div class="quote-line quote-line--total">
          <span>应付</span><span class="sent-price">¥{{ quote.payableYuan }}</span>
        </div>
      </div>

      <el-button type="primary" class="co-submit" :loading="submitting" @click="submit">
        提交订单并支付
      </el-button>
    </template>

    <el-dialog v-model="paid" title="支付成功" width="80%">
      <p>订单号：{{ paidOrderNo }}</p>
      <p>实付金额：<span class="sent-price">¥{{ paidAmount }}</span></p>
      <template #footer>
        <el-button @click="goOrders">查看订单</el-button>
        <el-button type="primary" @click="goHome">返回首页</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { cartApi, type QuoteVO } from '@/api/cart';
import { orderApi } from '@/api/order';
import { paymentApi, scheduleMockPayCallback } from '@/api/payment';
import { useCartStore, type BuyNowItem } from '@/store/modules/useCartStore';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { createIdempotencyScope } from '@/utils/idempotent';

const router = useRouter();
const cart = useCartStore();
const auth = useAuthStore();

interface PayItem { skuId: number; qty: number; name: string; }

const contact = ref('');
const quote = ref<QuoteVO | null>(null);
const submitting = ref(false);
const paid = ref(false);
const paidOrderNo = ref('');
const paidAmount = ref('');
/** UV 定制件标记：勾选中后下单行携带 customFlag，后端即时生成 custom_order 供上传图稿。 */
const customFlag = ref(false);
/** 本地持有「立即购买」项：进入即捕获并清空 store，避免污染后续购物车结算 */
const buyNowLocal = ref<BuyNowItem | null>(null);

/** 取价参数：不传折扣率会被后端反序列化成 0（等价「打到 0 折」），导致低于底价被拒 */
const PRICE_ARGS = {
  customerTypeCode: 'PERSON',
  memberDiscountRate: 100,
  agreementDiscountRate: 100,
  allowStacking: false,
  ladderTiers: [],
};
function quoteItems() {
  return items.value.map((i) => ({ skuId: i.skuId, qty: i.qty, ...PRICE_ARGS }));
}
/** 下单行才携带 customFlag：试算接口契约不含该字段，避免向前兼容问题。 */
function orderItems() {
  return quoteItems().map((i) => ({ ...i, customFlag: customFlag.value }));
}

const orderIdem = createIdempotencyScope('user-order');
const payIdem = createIdempotencyScope('user-pay');

const items = computed<PayItem[]>(() => {
  const bn = buyNowLocal.value;
  if (bn) return [{ skuId: bn.skuId, qty: bn.qty, name: `${bn.name} ${bn.spec}`.trim() }];
  return cart.selectedItems.map((i) => ({ skuId: i.skuId, qty: i.qty, name: i.skuName || `SKU ${i.skuId}` }));
});

function goProducts(): void { router.push('/products'); }
function goOrders(): void { router.push('/orders'); }
function goHome(): void { router.push('/home'); }

onMounted(async () => {
  buyNowLocal.value = cart.buyNow;
  await cart.clearBuyNow();
  try {
    quote.value = await unwrap(cartApi.checkoutPreview({
      availablePoints: 0,
      freightFreeThresholdCent: 0,
      freightFeeCent: 0,
      items: quoteItems(),
    }));
  } catch {
    quote.value = null;
  }
});

async function submit(): Promise<void> {
  const customerId = auth.userId;
  if (!customerId) {
    ElMessage.warning('请先登录');
    router.replace('/login');
    return;
  }
  if (!quote.value) {
    ElMessage.warning('订单信息暂不可用');
    return;
  }
  submitting.value = true;
  // 真源：buyNowLocal 非空 = 「立即购买」直购（不涉及购物车），为空 = 购物车结算
  const fromBuyNow = Boolean(buyNowLocal.value);
  try {
    const order = await unwrap(orderApi.create({
      customerId,
      idemKey: orderIdem.getOrCreate(),
      availablePoints: 0,
      freightFreeThresholdCent: 0,
      freightFeeCent: 0,
      items: orderItems(),
    }, { headers: orderIdem.headers() }));

    // 下单成功：购物车结算才清理已选行；「立即购买」不碰购物车，避免误删用户已加购商品
    if (!fromBuyNow) {
      const ids = cart.selectedItems.map((i) => i.itemId);
      for (const id of ids) await cart.remove(id);
    }
    orderIdem.clear();

    const pay = await unwrap(paymentApi.create({
      orderNo: order.orderNo,
      amountCent: order.payableCent ?? 0,
      channel: 'BALANCE',
      idemKey: payIdem.getOrCreate(),
    }, { headers: payIdem.headers() }));
    payIdem.clear();

    // 模拟第三方渠道异步通知：1.2s 后回调后端，把订单 WAIT_PAY → WAIT_SHIP
    scheduleMockPayCallback({
      txnNo: pay.txnNo ?? '',
      orderNo: order.orderNo,
      amountCent: Number(pay.amountCent ?? order.payableCent ?? 0),
      channel: 'BALANCE',
      payChannelTradeNo: `MOCK-${order.orderNo}-${Date.now()}`,
      paidAtMillis: Date.now(),
    });

    paidOrderNo.value = order.orderNo;
    paidAmount.value = pay.amountYuan || quote.value.payableYuan;
    paid.value = true;
  } catch (e) {
    ElMessage.error((e as Error).message || '下单失败');
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.co-custom { margin-top: 10px; }
.co-line {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #333;
  padding: 4px 0;
}
.quote-line {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #555;
  padding: 3px 0;
}
.quote-line--total {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
  border-top: 1px dashed #eee;
  margin-top: 4px;
  padding-top: 8px;
}
.co-submit {
  width: calc(100% - 20px);
  margin: 10px;
}
</style>
