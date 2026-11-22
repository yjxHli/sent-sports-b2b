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
          <span class="co-line__name">
            {{ it.name }}
            <!--
              取价来源提示（V54）：结算价由服务端取价引擎决定，可能是一客一价 / 企业协议价 /
              秒杀价 / 阶梯价 / 会员价。不标出来的话，用户看到的价格和详情页不一致会以为被坑。
            -->
            <span
              v-if="sourceLabelFor(it.skuId)"
              class="co-line__source"
              :data-testid="`checkout-price-source-${it.skuId}`"
            >
              {{ sourceLabelFor(it.skuId) }}
            </span>
          </span>
          <span>×{{ it.qty }}</span>
        </div>
        <el-checkbox v-model="customFlag" class="co-custom" :disabled="paid" @change="refreshQuote">
          含 UV 定制件（下单后在「订单详情」上传图稿，客服审图后生产）
        </el-checkbox>
      </div>

      <div v-if="quote" class="sent-card">
        <div class="quote-line"><span>商品金额</span><span>¥{{ quote.goodsAmountYuan }}</span></div>
        <div v-if="Number(quote.customFeeCent ?? 0) > 0" class="quote-line">
          <span>定制费（10 元/件）</span><span>¥{{ quote.customFeeYuan }}</span>
        </div>
        <div class="quote-line"><span>运费</span><span>¥{{ quote.freightYuan }}</span></div>
        <div v-if="couponOptions.length" class="quote-line quote-line--coupon">
          <span>优惠券</span>
          <el-select
            v-model="selectedCouponId"
            clearable
            placeholder="不使用优惠券"
            popper-class="checkout-coupon-popper"
            data-testid="checkout-coupon"
            style="width: 220px"
            @change="onCouponChange"
          >
            <el-option
              v-for="c in couponOptions"
              :key="c.couponId"
              :label="couponLabel(c)"
              :class="couponOptionClass(c)"
              :value="Number(c.couponId)"
            />
          </el-select>
        </div>
        <p v-if="couponOptions.length" class="quote-coupon-hint">灰色券未满足当前订单使用要求</p>
        <div v-if="Number(quote.couponDiscountCent ?? 0) > 0" class="quote-line quote-line--discount">
          <span>券抵扣</span><span class="sent-price">-¥{{ quote.couponDiscountYuan }}</span>
        </div>
        <!-- 积分抵扣：余额来自后端 /api/member/points，实际抵扣额只认试算返回值 -->
        <div class="quote-line quote-line--points" data-testid="checkout-points-row">
          <span class="co-points__label">
            <el-switch
              v-model="usePoints"
              data-testid="checkout-use-points"
              :disabled="availablePoints <= 0"
              @change="onPointsToggle"
            />
            使用积分抵扣
          </span>
          <el-input-number
            v-if="usePoints"
            v-model="pointsToUse"
            data-testid="checkout-points-input"
            :data-max="availablePoints"
            :min="0"
            :max="availablePoints"
            :step="1"
            controls-position="right"
            @change="onPointsChange"
          />
        </div>
        <p v-if="availablePoints > 0" class="quote-points-hint" data-testid="checkout-points-hint">
          可用 {{ availablePoints }} 积分，本单最高可抵扣 30%
        </p>
        <div
          v-if="Number(quote.pointDeductCent ?? 0) > 0"
          class="quote-line quote-line--discount"
          data-testid="checkout-point-deduct"
        >
          <span>积分抵扣</span><span class="sent-price">-¥{{ quote.pointDeductYuan }}</span>
        </div>
        <div class="quote-line quote-line--total">
          <span>应付</span><span class="sent-price">¥{{ quote.payableYuan }}</span>
        </div>
      </div>

      <!-- 定制退款口径（2026-11-21）：必须在支付按钮上方讲清楚，别等下单后才发现不能退 -->
      <div
        v-if="customFlag && !paid"
        class="co-custom-refund-tip"
        data-testid="checkout-custom-refund-tip"
      >
        <strong>定制商品在审核通过前支持退款，一旦开始生产将不支持退款。</strong>
        <span>审核通过但尚未排产的，退款需客服先与生产确认是否已备料。</span>
      </div>

      <div class="co-submit-row">
        <el-button type="primary" class="co-submit" :loading="submitting" :disabled="paid" @click="submitAndPay">
          提交订单并支付
        </el-button>
        <!-- 暂不支付：只生成订单，进详情页给你 5 分钟倒计时，超时自动取消 -->
        <el-button class="co-submit co-submit--later" :loading="submitting" :disabled="paid" @click="submitLater">
          暂不支付
        </el-button>
      </div>
      <p v-if="paid" class="co-submit-hint">订单已提交并支付成功，可在「我的订单」查看进度</p>
      <p v-else class="co-submit-hint">暂不支付也可下单，请在 5 分钟内完成支付，超时订单将自动取消</p>
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
import { couponApi, type CouponVO } from '@/api/coupon';
import { memberApi } from '@/api/member';
import { orderApi } from '@/api/order';
import { addressApi, type AddressVO } from '@/api/address';
import { customApi, readPendingDraft, clearPendingDraft } from '@/api/custom';
import { paymentApi, scheduleMockPayCallback } from '@/api/payment';
import { fmtCent } from '@sent/shared';
import { useCartStore, type BuyNowItem } from '@/store/modules/useCartStore';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { createIdempotencyScope } from '@/utils/idempotent';

const router = useRouter();
const cart = useCartStore();
const auth = useAuthStore();

interface PayItem { skuId: number; qty: number; name: string; }

const quote = ref<QuoteVO | null>(null);
/**
 * 结算页券列表：展示用户未使用的全部券，按「当前订单是否可用」分两级样式。
 * 可用与否只认后端 `/coupons/usable` 的结果，门槛口径 = 商品额 + 定制费（不含运费），
 * 前端不自己拿 threshold 重算，避免和下单时的二次校验漂移。
 */
const allCoupons = ref<CouponVO[]>([]);
const usableCouponIds = ref<Set<number>>(new Set());
const selectedCouponId = ref<number | undefined>(undefined);
/** 上一次真正可用的券：点灰券被拦截后要回到这里，不能让 v-model 留下非法值 */
const lastUsableCouponId = ref<number | undefined>(undefined);
const submitting = ref(false);
const paid = ref(false);
const paidOrderNo = ref('');
const paidAmount = ref('');
/**
 * UV 定制件标记：勾选中后下单行携带 customFlag，后端即时生成 custom_order 供上传图稿。
 * 默认不勾：勾选会按件加收定制费（10 元/件），不能因为本地残留过一张旧图稿就替用户加钱。
 */
const customFlag = ref(false);
/** 积分抵扣：余额来自后端（/api/member/points），输入上限与最终提交值都以此为准 */
const availablePoints = ref(0);
const usePoints = ref(false);
const pointsToUse = ref(0);
/** 本地持有「立即购买」项：进入即捕获并清空 store，避免污染后续购物车结算 */
const buyNowLocal = ref<BuyNowItem | null>(null);
/**
 * 已提交订单的商品行快照。
 *
 * 下单成功后会把购物车已选行删掉（必须删，否则用户回购物车还能重复下单），
 * 此时 `items` 会变空 —— 结算页就退化成「没有可结算的商品 + 去选购」空态，
 * 而支付成功弹窗正盖在它上面（用户 2026-11-21 反馈「弹窗背景是空白确认订单页」）。
 * 这里在下单成功瞬间留一份快照，支付成功弹窗期间继续按这份快照渲染，背景仍是这一单的商品。
 */
const submittedItems = ref<PayItem[] | null>(null);

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
  return items.value.map((i) => ({ skuId: i.skuId, qty: i.qty, ...PRICE_ARGS,
    customFlag: customFlag.value }));
}
function orderItems() {
  return quoteItems();
}

const orderIdem = createIdempotencyScope('user-order');
const payIdem = createIdempotencyScope('user-pay');

const items = computed<PayItem[]>(() => {
  // 下单成功后购物车已选行会被清空，这里优先用快照，避免支付成功弹窗背后变成空态
  const snapshot = submittedItems.value;
  if (snapshot) return snapshot;
  const bn = buyNowLocal.value;
  if (bn) return [{ skuId: bn.skuId, qty: bn.qty, name: `${bn.name} ${bn.spec}`.trim() }];
  return cart.selectedItems.map((i) => ({ skuId: i.skuId, qty: i.qty, name: i.skuName || `SKU ${i.skuId}` }));
});

/**
 * 取价来源文案（V54）：秒杀价 / 一客一价 / 企业协议价 都可能低于商品零售价，
 * 用户只看到「金额对不上详情页」会以为被坑，所以逐行标出来源。
 * RETAIL（零售价）是默认口径，标出来只会是噪音，故不显示。
 */
const SOURCE_LABELS: Record<string, string> = {
  CUSTOMER_SKU: '一客一价',
  AGREEMENT: '企业协议价',
  FLASH_SALE: '秒杀价',
  LADDER: '阶梯价',
  MEMBER: '会员价',
  STACKED: '叠加优惠',
};

function sourceLabelFor(skuId: number | string): string {
  const line = quote.value?.lines?.find((l) => String(l.skuId) === String(skuId));
  if (!line) return '';
  return SOURCE_LABELS[line.sourceCode] ?? '';
}

function goProducts(): void { router.push('/products'); }
/** 支付成功「查看订单」→ 直接进该订单详情（不再先落到列表） */
function goOrders(): void {
  if (paidOrderNo.value) router.push(`/order/${paidOrderNo.value}`);
  else router.push('/orders');
}
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


/**
 * 券门槛口径 = 商品额 + 定制费，不含运费。
 * 与后端 CartQuoteUseCase / CreateOrderUseCase 完全一致，前后端不会一处能用一处不能用。
 */
function couponEligibleCent(): number {
  const q = quote.value;
  if (!q) return 0;
  return Number(q.goodsAmountCent ?? 0) + Number(q.customFeeCent ?? 0);
}

/** 可用券排前面、不可用券排后面；已使用的券不再出现在结算选择里 */
const couponOptions = computed<CouponVO[]>(() => {
  const candidates = allCoupons.value.filter((c) => c.status !== 'USED');
  return candidates.sort((a, b) => {
    const au = usableCouponIds.value.has(Number(a.couponId)) ? 0 : 1;
    const bu = usableCouponIds.value.has(Number(b.couponId)) ? 0 : 1;
    return au - bu || Number(b.amountCent) - Number(a.amountCent);
  });
});

function couponIsUsable(c: CouponVO): boolean {
  return usableCouponIds.value.has(Number(c.couponId));
}

/**
 * 券名本身已经带面额（如「8元券」），这里只补门槛/状态，不再拼 ¥ 前缀，
 * 否则下拉里会出现「¥8 8元券」这种价格重复两遍的展示。
 */
function couponLabel(c: CouponVO): string {
  const name = c.name || '优惠券';
  if (c.status === 'EXPIRED') return `${name} · 已过期`;
  const threshold = Number(c.thresholdCent ?? 0);
  return threshold > 0 ? `${name} · 满${fmtCent(threshold)}元可用` : `${name} · 无门槛`;
}

function couponOptionClass(c: CouponVO): string {
  return couponIsUsable(c) ? 'coupon-option--usable' : 'coupon-option--unusable';
}

/** 点灰券：提示未满足要求并把 v-model 回退到上一次可用的券，不生成任何抵扣请求 */
function onCouponChange(value: number | undefined): void {
  const id = value === undefined || value === null ? undefined : Number(value);
  if (id !== undefined && !usableCouponIds.value.has(id)) {
    ElMessage.warning('该优惠券未满足使用要求，无法使用');
    selectedCouponId.value = lastUsableCouponId.value;
    void loadQuote();
    return;
  }
  lastUsableCouponId.value = id;
  void loadQuote();
}

/** 同时拉「我的券」与「当前订单可用券」；订单金额变化后已选券失效则自动取消 */
async function loadCoupons(): Promise<void> {
  const goodsCent = couponEligibleCent();
  if (goodsCent <= 0) {
    allCoupons.value = [];
    usableCouponIds.value = new Set();
    selectedCouponId.value = undefined;
    lastUsableCouponId.value = undefined;
    return;
  }
  try {
    const [mine, usable] = await Promise.all([
      unwrap(couponApi.mine()),
      // 带上购物车 SKU：品类券只有命中范围内商品才算可用，否则选了也抵扣 0
      unwrap(couponApi.usable(goodsCent, items.value.map((i) => i.skuId))),
    ]);
    allCoupons.value = mine ?? [];
    usableCouponIds.value = new Set((usable ?? []).map((c) => Number(c.couponId)));
  } catch {
    allCoupons.value = [];
    usableCouponIds.value = new Set();
  }
  if (selectedCouponId.value !== undefined && !usableCouponIds.value.has(selectedCouponId.value)) {
    selectedCouponId.value = undefined;
    lastUsableCouponId.value = undefined;
  }
}

/** 试算与下单共用的积分参数：开关关闭时一律传 0，避免残留输入值偷偷生效 */
function effectivePoints(): number {
  return usePoints.value ? Math.max(0, Math.min(pointsToUse.value, availablePoints.value)) : 0;
}

/** 余额以接口返回为准；读失败按 0 处理，不让用户勾一个必然被后端拒的开关 */
async function loadPoints(): Promise<void> {
  try {
    const pt = await unwrap(memberApi.points());
    availablePoints.value = Math.max(0, Number(pt?.availablePoints ?? 0));
    pointsToUse.value = availablePoints.value;
  } catch {
    availablePoints.value = 0;
    pointsToUse.value = 0;
    usePoints.value = false;
  }
}

function onPointsToggle(enabled: boolean): void {
  pointsToUse.value = enabled ? availablePoints.value : 0;
  void loadQuote();
}

/** 输入值收敛到 [0, 可用积分]；30% 上限由后端试算给出，前端不复制规则 */
function onPointsChange(value: number | undefined): void {
  const n = Number(value ?? 0);
  pointsToUse.value = Number.isFinite(n)
    ? Math.max(0, Math.min(Math.floor(n), availablePoints.value))
    : 0;
  void loadQuote();
}

async function loadQuote(): Promise<void> {
  try {
    quote.value = await unwrap(cartApi.checkoutPreview({
      availablePoints: effectivePoints(),
      freightFreeThresholdCent: 0,
      freightFeeCent: 0,
      couponId: selectedCouponId.value,
      items: quoteItems(),
    }));
  } catch {
    quote.value = null;
  }
}

/** 定制勾选变化会改变券门槛，取价与可用券列表必须一起刷新 */
async function refreshQuote(): Promise<void> {
  await loadQuote();
  await loadCoupons();
}

onMounted(async () => {
  buyNowLocal.value = cart.buyNow;
  await cart.clearBuyNow();
  await Promise.all([loadAddresses(), loadPoints()]);
  await refreshQuote();
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

/**
 * 提交订单。
 * @param payNow true = 立即支付（原有链路）；false = 「暂不支付」，只下单并跳到详情页，
 *               由详情页的 5 分钟倒计时提醒 + 后端 OrderTimeoutJob 负责超时关单
 */
async function submit(payNow: boolean): Promise<void> {
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
      // 与试算完全同一个参数：后端重新算一次，金额不会因前端残留状态分叉
      availablePoints: effectivePoints(),
      freightFreeThresholdCent: 0,
      freightFeeCent: 0,
      couponId: selectedCouponId.value,
      items: orderItems(),
    }, { headers: orderIdem.headers() }));

    // 下单成功：先留一份商品行快照，再清购物车已选行——清空后 `items` 会变空，
    // 支付成功弹窗背后的结算页就会退化成「去选购」空态（2026-11-21 用户反馈）。
    submittedItems.value = items.value.map((i) => ({ ...i }));

    // 下单成功：购物车结算才清理已选行；「立即购买」不碰购物车，避免误删用户已加购商品
    if (!fromBuyNow) {
      const ids = cart.selectedItems.map((i) => i.itemId);
      for (const id of ids) await cart.remove(id);
    }
    orderIdem.clear();

    // 「暂不支付」：订单已经落库（WAIT_PAY + 5 分钟 expire_at），直接进详情页看倒计时。
    // 不在这里补任何定时器——关单只有后端 OrderTimeoutJob 一个执行者，前端只负责展示。
    if (!payNow) {
      void attachPendingDraft();
      ElMessage.success('订单已生成，请在 5 分钟内完成支付');
      await router.push(`/order/${order.orderNo}`);
      return;
    }

    const pay = await unwrap(paymentApi.create({
      orderNo: order.orderNo,
      amountCent: order.payableCent ?? 0,
      channel: 'BALANCE',
      idemKey: payIdem.getOrCreate(),
    }, { headers: payIdem.headers() }));
    payIdem.clear();

    // 模拟第三方渠道异步通知：1.2s 后回调后端（回放后端下发的签名参数），把订单 WAIT_PAY → WAIT_SHIP
    scheduleMockPayCallback(pay);

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

/** 立即支付（按钮回调不能直接绑 submit，否则 MouseEvent 会被当成 payNow） */
function submitAndPay(): void {
  void submit(true);
}

/** 暂不支付：只下单，进详情页倒计时 */
function submitLater(): void {
  void submit(false);
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
.co-line__name {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
/* 取价来源小标签：只在命中非默认口径（秒杀/协议/一客一价等）时出现 */
.co-line__source {
  flex: none;
  font-size: 11px;
  line-height: 1;
  padding: 3px 7px;
  border-radius: 9px;
  color: var(--sent-accent);
  background: #fff2e6;
  border: 1px solid #ffe0c7;
}
.quote-line {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #555;
  padding: 3px 0;
}
.quote-line--coupon { align-items: center; min-height: 34px; }
.quote-coupon-hint { margin: 0 0 4px; text-align: right; font-size: 11px; color: var(--sent-muted); }
.quote-line--points { align-items: center; min-height: 34px; }
.co-points__label { display: inline-flex; align-items: center; gap: 8px; }
.quote-points-hint { margin: 0 0 4px; text-align: right; font-size: 11px; color: var(--sent-muted); }
.quote-line--discount span:last-child { color: var(--sent-accent-dark); }
.quote-line--total {
  font-size: 15px;
  font-weight: 600;
  color: #1f2329;
  border-top: 1px dashed #eee;
  margin-top: 4px;
  padding-top: 8px;
}
/* 定制退款提示：琥珀底＋左描边，比普通灰字提示更「显眼」，但不至于像报错 */
.co-custom-refund-tip {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 10px 10px 0;
  padding: 10px 12px;
  border-radius: 8px;
  border-left: 3px solid #e6a23c;
  background: #fdf6ec;
  font-size: 12px;
  line-height: 1.6;
  color: #8a5a00;
}
.co-custom-refund-tip strong {
  font-size: 13px;
  font-weight: 600;
  color: #7a4a00;
}
/* 两个提交入口并排：主操作「提交订单并支付」、次操作「暂不支付」 */
.co-submit-row {
  display: flex;
  gap: 10px;
  margin: 10px;
}
.co-submit-row .co-submit {
  flex: 1;
  width: auto;
  margin: 0;
}
.co-submit--later {
  flex: none;
}
.co-submit-hint {
  margin: 0 10px 14px;
  text-align: center;
  font-size: 12px;
  color: var(--sent-muted);
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

<!-- ElementPlus 下拉浮层挂在 body 上，scoped 样式到不了，这里用 popper-class 单独生效 -->
<style>
.checkout-coupon-popper .el-select-dropdown__item.coupon-option--usable {
  color: var(--sent-accent-dark) !important;
  font-weight: 600;
}
.checkout-coupon-popper .el-select-dropdown__item.coupon-option--unusable {
  color: #a8abb2 !important;
  background: #fafafa;
}
</style>
