<template>
  <div>
    <div class="sent-bar"><strong>确认订单</strong></div>

    <el-empty v-if="items.length === 0" description="没有可结算的商品">
      <el-button type="primary" @click="goProducts">去选购</el-button>
    </el-empty>

    <template v-else>
      <!-- ① 收货地址：默认地址预选；一条都没有时引导新增（下单必填） -->
      <div class="sent-card co-addr">
        <div class="sent-title co-addr__title">
          <span>收货信息</span>
          <button type="button" class="co-addr__add" @click="openCreate">＋ 新增地址</button>
        </div>

        <div v-if="addresses.length === 0" class="co-addr__empty">
          <p>还没有收货地址，填一条就能下单</p>
          <el-button type="primary" plain size="small" @click="openCreate">新增收货地址</el-button>
        </div>

        <ul v-else class="co-addr__list">
          <li
            v-for="a in addresses"
            :key="String(a.id)"
            class="co-addr__item"
            :class="{ 'co-addr__item--on': String(a.id) === String(selectedId) }"
            @click="select(a)"
          >
            <span class="co-addr__radio" aria-hidden="true" />
            <div class="co-addr__main">
              <div class="co-addr__row">
                <b>{{ a.receiver }}</b>
                <span class="co-addr__phone">{{ a.phone }}</span>
                <span v-if="a.isDefault" class="co-addr__tag">默认</span>
              </div>
              <div class="co-addr__detail">{{ fullAddress(a) }}</div>
            </div>
            <div class="co-addr__ops" @click.stop>
              <button v-if="!a.isDefault" type="button" class="co-mini" @click="makeDefault(a)">设为默认</button>
              <button type="button" class="co-mini co-mini--danger" @click="removeAddress(a)">删除</button>
            </div>
          </li>
        </ul>
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

    <!-- 新增收货地址 -->
    <el-dialog v-model="addrVisible" title="新增收货地址" width="86%">
      <el-form label-width="72px" class="co-form">
        <el-form-item label="收件人">
          <el-input v-model="form.receiver" placeholder="请填写收件人姓名" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="请填写 11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="省份">
          <el-input v-model="form.province" placeholder="如：江苏省" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="form.city" placeholder="如：南京市" />
        </el-form-item>
        <el-form-item label="区/县">
          <el-input v-model="form.district" placeholder="如：玄武区" />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="form.detail" type="textarea" :rows="2" placeholder="街道、门牌号、楼层等" />
        </el-form-item>
        <el-form-item label="默认">
          <el-checkbox v-model="form.isDefault">设为默认收货地址</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addrVisible = false">取消</el-button>
        <el-button type="primary" :loading="addrSaving" @click="saveAddress">保存并使用</el-button>
      </template>
    </el-dialog>

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
/**
 * 确认订单（结算页）。
 *
 * 收货地址闭环（2026-11-15）：
 *   1) 进页先拉地址簿，默认勾选 {@code isDefault} 的那条，没有默认就勾第一条；
 *   2) 一条地址都没有 → 直接引导新增（下单接口强制要 addressId，不填必然 400）；
 *   3) 新增 / 删除 / 设为默认都在本页完成，不必跳「我的账户」；
 *   4) 提交时把 {@code addressId} 一并带给后端，后端据此固化
 *      {@code orders.address_snapshot_json}——之后用户改地址簿不会影响历史订单。
 */
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { unwrap } from '@/utils/http';
import { cartApi, type QuoteVO } from '@/api/cart';
import { orderApi } from '@/api/order';
import { addressApi, type AddressVO } from '@/api/address';
import { customApi, readPendingDraft, clearPendingDraft } from '@/api/custom';
import { paymentApi, scheduleMockPayCallback } from '@/api/payment';
import { useCartStore, type BuyNowItem } from '@/store/modules/useCartStore';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { createIdempotencyScope } from '@/utils/idempotent';

const router = useRouter();
const cart = useCartStore();
const auth = useAuthStore();

interface PayItem { skuId: number; qty: number; name: string; }

const quote = ref<QuoteVO | null>(null);
const submitting = ref(false);
const paid = ref(false);
const paidOrderNo = ref('');
const paidAmount = ref('');
/** UV 定制件标记：勾选中后下单行携带 customFlag，后端即时生成 custom_order 供上传图稿。 */
const customFlag = ref(false);
/** 本地持有「立即购买」项：进入即捕获并清空 store，避免污染后续购物车结算 */
const buyNowLocal = ref<BuyNowItem | null>(null);

/** 地址簿与当前选中项（后端 Long 序列化成字符串，统一按 String 比较） */
const addresses = ref<AddressVO[]>([]);
const selectedId = ref<string>('');
const addrVisible = ref(false);
const addrSaving = ref(false);
const form = reactive({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false,
});

const selectedAddress = computed(
  () => addresses.value.find((a) => String(a.id) === selectedId.value) ?? null,
);

/** 省市区拼成一行；缺项自动跳过，不产生多余空格 */
function fullAddress(a: AddressVO): string {
  return [a.province, a.city, a.district, a.detail].filter(Boolean).join(' ');
}

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

/** 拉地址簿并自动选中：优先默认地址，其次第一条 */
async function loadAddresses(keepSelection = false): Promise<void> {
  try {
    addresses.value = (await unwrap(addressApi.list())) ?? [];
  } catch {
    addresses.value = [];
  }
  if (keepSelection && addresses.value.some((a) => String(a.id) === selectedId.value)) {
    return;
  }
  const preferred = addresses.value.find((a) => a.isDefault) ?? addresses.value[0];
  selectedId.value = preferred ? String(preferred.id) : '';
}

function select(a: AddressVO): void {
  selectedId.value = String(a.id);
}

function resetForm(): void {
  form.receiver = '';
  form.phone = '';
  form.province = '';
  form.city = '';
  form.district = '';
  form.detail = '';
  // 第一条地址后端会自动置默认，界面上把勾选状态也表达出来
  form.isDefault = addresses.value.length === 0;
}

function openCreate(): void {
  resetForm();
  addrVisible.value = true;
}

async function saveAddress(): Promise<void> {
  if (!form.receiver.trim() || !form.phone.trim() || !form.detail.trim()) {
    ElMessage.warning('收件人、手机号、详细地址为必填项');
    return;
  }
  addrSaving.value = true;
  try {
    const created = await unwrap(addressApi.create({
      receiver: form.receiver.trim(),
      phone: form.phone.trim(),
      province: form.province.trim() || null,
      city: form.city.trim() || null,
      district: form.district.trim() || null,
      detail: form.detail.trim(),
      isDefault: form.isDefault,
    }));
    addrVisible.value = false;
    await loadAddresses();
    // 新增后直接选中它，省得用户再点一次
    if (created?.id !== undefined) selectedId.value = String(created.id);
    ElMessage.success('地址已保存');
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败');
  } finally {
    addrSaving.value = false;
  }
}

async function makeDefault(a: AddressVO): Promise<void> {
  try {
    await unwrap(addressApi.setDefault(Number(a.id)));
    await loadAddresses();
    selectedId.value = String(a.id);
    ElMessage.success('已设为默认地址');
  } catch (e) {
    ElMessage.error((e as Error).message || '设置失败');
  }
}

async function removeAddress(a: AddressVO): Promise<void> {
  try {
    await ElMessageBox.confirm('删除后该地址不能用于新订单，历史订单的收货信息不受影响。', '删除地址', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    await unwrap(addressApi.remove(Number(a.id)));
    await loadAddresses();
    ElMessage.success('已删除');
  } catch (e) {
    ElMessage.error((e as Error).message || '删除失败');
  }
}

onMounted(async () => {
  buyNowLocal.value = cart.buyNow;
  await cart.clearBuyNow();
  await loadAddresses();
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

/**
 * 把商品页选好的定制图稿补交到刚生成的定制单。
 * 下单时 custom_order 才创建（WAIT_DRAFT），图稿必须在这一步之后提交，否则管理端审图台无图可看。
 * best-effort：失败只提示去「我的定制」补交，不影响已付款订单。
 */
async function attachPendingDraft(): Promise<void> {
  if (!customFlag.value) return;
  const pending = readPendingDraft();
  if (!pending) return;
  try {
    const page = await unwrap(customApi.my({ page: 1, size: 5 }));
    const target = (page?.list ?? []).find((c) => c.status === 'WAIT_DRAFT');
    if (!target) return;
    await unwrap(customApi.uploadDraft(target.customOrderId, pending));
    clearPendingDraft();
    ElMessage.success('定制图稿已提交审图');
  } catch {
    ElMessage.warning('图稿提交失败，可在「我的定制」补交');
  }
}

async function submit(): Promise<void> {
  const customerId = auth.userId;
  if (!customerId) {
    ElMessage.warning('请先登录');
    router.replace('/login');
    return;
  }
  // 没有地址就别发请求：后端会直接 400，前端先把话说清楚并把人引到地址区
  if (!selectedAddress.value) {
    ElMessage.warning('请先选择收货地址');
    document.querySelector('.co-addr')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
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
      addressId: Number(selectedAddress.value.id),
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
    void attachPendingDraft();
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

/* ---------- 收货地址 ---------- */
.co-addr__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.co-addr__add {
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  font-family: inherit;
  color: var(--sent-primary);
  cursor: pointer;
}
.co-addr__empty {
  padding: 14px 0 6px;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}
.co-addr__empty p { margin: 0 0 10px; }

.co-addr__list {
  list-style: none;
  margin: 8px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.co-addr__item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid #ebedf0;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.co-addr__item--on {
  border-color: var(--sent-primary);
  background: #f3faf6;
}
.co-addr__radio {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 1.5px solid #c9ced6;
  flex: none;
  margin-top: 3px;
  position: relative;
}
.co-addr__item--on .co-addr__radio {
  border-color: var(--sent-primary);
}
.co-addr__item--on .co-addr__radio::after {
  content: '';
  position: absolute;
  inset: 2px;
  border-radius: 50%;
  background: var(--sent-primary);
}
.co-addr__main { flex: 1; min-width: 0; }
.co-addr__row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--sent-text);
}
.co-addr__phone { color: var(--sent-muted); font-size: 12px; }
.co-addr__tag {
  font-size: 10px;
  color: var(--sent-primary);
  border: 1px solid var(--sent-primary);
  border-radius: 3px;
  padding: 0 4px;
  line-height: 15px;
}
.co-addr__detail {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--sent-muted);
  word-break: break-all;
}
.co-addr__ops {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: none;
}
.co-mini {
  border: none;
  background: none;
  padding: 0;
  font-size: 11px;
  font-family: inherit;
  color: var(--sent-muted);
  cursor: pointer;
}
.co-mini:hover { color: var(--sent-primary); }
.co-mini--danger:hover { color: #e5484d; }
</style>
