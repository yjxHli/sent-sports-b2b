<template>
  <div class="container order-page">
    <!-- 状态 Tab（仿淘宝「已买到的宝贝」） -->
    <div class="order-tabs">
      <button
        v-for="t in TABS"
        :key="t.key || 'all'"
        type="button"
        class="order-tab"
        :class="{ 'order-tab--active': activeTab === t.key }"
        @click="switchTab(t.key)"
      >
        {{ t.label }}
      </button>
      <span class="order-tabs__total">共 {{ total }} 笔</span>
    </div>

    <div v-if="loading" class="list-hint">加载中…</div>
    <el-empty v-else-if="orders.length === 0" :description="emptyText" />

    <template v-else>
      <div class="order-list">
        <div v-for="o in orders" :key="o.orderNo" class="order-card">
          <!-- 卡片头：下单时间 / 订单号 / 店铺 / 状态 -->
          <div class="order-card__head">
            <span class="order-card__time">{{ fmtDateTime(o.createdAt) }}</span>
            <span class="order-card__no">订单号：{{ o.orderNo }}</span>
            <span class="order-card__shop">森特体育自营店</span>
            <span class="order-card__status" :class="`order-card__status--${toneOf(o.status)}`">
              {{ label(o.status) }}
            </span>
          </div>

          <!-- 卡片体：商品行 + 实付款 -->
          <div class="order-card__body" @click="goDetail(o.orderNo)">
            <div class="order-items">
              <div v-for="(it, idx) in itemsOf(o)" :key="idx" class="order-item">
                <ProductThumb
                  class="order-item__thumb"
                  :name="it.title || ''"
                  :seed="it.skuId || `${o.orderNo}-${idx}`"
                />
                <div class="order-item__info">
                  <button
                    type="button"
                    class="order-item__title"
                    :title="`查看「${it.title || '商品'}」详情`"
                    @click.stop="openProduct(it)"
                  >{{ it.title || '商品' }}</button>
                  <div class="order-item__spec">{{ it.spec || '默认规格' }}</div>
                </div>
                <div class="order-item__price">¥{{ it.unitPriceYuan || '0.00' }}</div>
                <div class="order-item__qty">x{{ it.qty ?? 1 }}</div>
              </div>
              <div v-if="itemsOf(o).length === 0" class="order-item order-item--empty">
                该订单暂无商品行
              </div>
            </div>

            <div class="order-card__pay">
              <span class="order-card__pay-label">实付款</span>
              <b class="order-card__pay-amount">¥{{ o.payableYuan || '0.00' }}</b>
            </div>
          </div>

          <!-- 卡片操作 -->
          <div class="order-card__ops">
            <el-button size="small" @click="goDetail(o.orderNo)">查看详情</el-button>
            <el-button
              v-if="canInvoice(o)"
              size="small"
              @click="openInvoice(o)"
            >
              申请开票
            </el-button>
            <el-button
              v-if="o.status === 'WAIT_PAY'"
              size="small"
              :loading="acting === o.orderNo"
              @click="cancel(o)"
            >
              取消订单
            </el-button>
            <el-button
              v-if="o.status === 'WAIT_PAY'"
              type="primary"
              size="small"
              :loading="acting === o.orderNo"
              @click="pay(o)"
            >
              去支付
            </el-button>
            <el-button
              v-if="o.status === 'WAIT_RECEIVE'"
              type="success"
              size="small"
              :loading="acting === o.orderNo"
              @click="confirm(o)"
            >
              确认收货
            </el-button>
            <el-button
              v-if="o.status === 'COMPLETED'"
              size="small"
              @click="buyAgain(o)"
            >
              再买一次
            </el-button>
          </div>
        </div>
      </div>

      <el-pagination
        v-if="total > size"
        class="list-page"
        background
        layout="prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPage"
      />
    </template>

    <InvoiceApplyDialog
      v-model="invoiceVisible"
      :order-no="invoiceOrderNo"
      :payable-cent="invoicePayableCent"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 我的订单（仿淘宝「已买到的宝贝」）：
 * 多状态 Tab（全部/待付款/待发货/待收货/已完成）+ 订单卡片（下单时间、订单号、商品行、实付款、操作）。
 *
 * 状态筛选由后端下推（`GET /api/orders?status=`），前端不做整表过滤，
 * 否则分页 total 与每页条数都会失真。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { fmtDateTime, statusLabel, statusTone, type StatusTone } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { orderApi, type OrderItemLine, type OrderListItem } from '@/api/order';
import { paymentApi, scheduleMockPayCallback } from '@/api/payment';
import ProductThumb from '@/components/ProductThumb.vue';
import InvoiceApplyDialog from '@/components/InvoiceApplyDialog.vue';

const route = useRoute();
const router = useRouter();

const TABS = [
  { key: '', label: '全部' },
  { key: 'WAIT_PAY', label: '待付款' },
  { key: 'WAIT_SHIP', label: '待发货' },
  { key: 'WAIT_RECEIVE', label: '待收货' },
  { key: 'COMPLETED', label: '已完成' },
];

const orders = ref<OrderListItem[]>([]);
const loading = ref(false);
const acting = ref('');
const total = ref(0);
const page = ref(1);
const size = ref(10);
const activeTab = ref<string>((route.query.status as string) || '');

/** 开票弹窗（列表里直接开票，不必先进详情） */
const invoiceVisible = ref(false);
const invoiceOrderNo = ref('');
const invoicePayableCent = ref(0);

const emptyText = computed(() =>
  activeTab.value ? '该状态下暂无订单' : '还没有订单，去逛逛吧');

function label(s: string | null): string { return statusLabel(s); }
function toneOf(s: string | null): StatusTone { return statusTone(s); }
function itemsOf(o: OrderListItem): OrderItemLine[] { return o.items ?? []; }
function goDetail(no: string): void { router.push(`/order/${no}`); }

/**
 * 点商品名进商品详情。
 * 商品详情接口按 SPU（productId）查，不是 skuId —— 旧实现拿 skuId 拼链接会落到
 * 「商品不存在或已下架」。历史订单行可能没有 productId，此时明确提示而不是跳死链。
 */
function openProduct(it: OrderItemLine): void {
  const pid = Number(it.productId ?? 0);
  if (!pid) {
    ElMessage.warning('该商品已下架，无法查看详情');
    return;
  }
  router.push(`/product/${pid}`);
}

/** 有实付金额才允许开票：未支付/支付中不给入口，整单退款后票面无依据也不给 */
/**
 * 开票入口口径（用户 2026-11-15 收紧）：只有**已完成**的订单才能申请开票。
 * 在途订单货还没到手、可能退换，票面金额与实际成交不一致，财务无法核销，
 * 因此列表页也不再给入口（后端同样按此口径校验，前端少一次无谓的 409）。
 */
function canInvoice(o: OrderListItem): boolean {
  if (o.status !== 'COMPLETED') return false;
  return o.payStatus === 'PAID' || o.payStatus === 'PART_REFUNDED';
}

function openInvoice(o: OrderListItem): void {
  invoiceOrderNo.value = o.orderNo;
  invoicePayableCent.value = Number(o.payableCent ?? 0);
  invoiceVisible.value = true;
}

function switchTab(key: string): void {
  if (activeTab.value === key) return;
  activeTab.value = key;
  page.value = 1;
  // 这里直接取数：watch 只在外部导航（浏览器前进/后退、直接改 URL）时兜底
  void load();
  router.replace({ path: '/orders', query: key ? { status: key } : {} });
}

async function onPage(p: number): Promise<void> {
  page.value = p;
  await load();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function pay(o: OrderListItem): Promise<void> {
  acting.value = o.orderNo;
  try {
    const payResult = await unwrap(paymentApi.create({
      orderNo: o.orderNo,
      amountCent: o.payableCent ?? 0,
      channel: 'BALANCE',
      idemKey: `pay_${o.orderNo}`,
    }));
    scheduleMockPayCallback({
      txnNo: payResult.txnNo ?? '',
      orderNo: o.orderNo,
      amountCent: Number(payResult.amountCent ?? o.payableCent ?? 0),
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

/** 再买一次：直接回该单第一个商品的详情页（按 productId，不再用 skuId 拼死链） */
async function buyAgain(o: OrderListItem): Promise<void> {
  const first = itemsOf(o)[0];
  const pid = Number(first?.productId ?? 0);
  if (!pid) {
    ElMessage.warning('缺少商品信息，无法再次购买');
    return;
  }
  router.push(`/product/${pid}`);
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const res = await unwrap(orderApi.list({
      page: page.value,
      size: size.value,
      status: activeTab.value || null,
    }));
    orders.value = res.list ?? [];
    total.value = Number(res.total ?? 0);
  } finally {
    loading.value = false;
  }
}

watch(
  () => route.query.status,
  (value) => {
    const next = (value as string) || '';
    if (next === activeTab.value) return;
    activeTab.value = next;
    page.value = 1;
    void load();
  },
);

onMounted(load);
</script>

<style scoped>
.order-page {
  padding-top: 12px;
}

/* ---------- 状态 Tab ---------- */
.order-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 8px 12px;
  margin-bottom: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.order-tab {
  border: none;
  background: transparent;
  color: var(--sent-text-2);
  font-size: 15px;
  padding: 9px 18px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  position: relative;
}
.order-tab:hover {
  color: var(--sent-primary);
}
.order-tab--active {
  color: var(--sent-primary);
  font-weight: 700;
  background: var(--sent-primary-soft);
}
.order-tab--active::after {
  content: '';
  position: absolute;
  left: 18px;
  right: 18px;
  bottom: 3px;
  height: 2px;
  border-radius: 1px;
  background: var(--sent-primary);
}
.order-tabs__total {
  margin-left: auto;
  font-size: 12px;
  color: var(--sent-muted);
}

/* ---------- 订单卡片 ---------- */
.order-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.order-card {
  background: #fff;
  border-radius: var(--sent-radius);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}
.order-card__head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  background: #fafbfc;
  border-bottom: 1px solid #f2f3f5;
  font-size: 12px;
  color: var(--sent-muted);
}
.order-card__no {
  color: var(--sent-text-2);
}
.order-card__shop {
  color: var(--sent-text-2);
}
.order-card__status {
  margin-left: auto;
  font-size: 13px;
  font-weight: 600;
  color: var(--sent-accent-dark);
}
.order-card__status--success {
  color: var(--sent-primary);
}
.order-card__status--info {
  color: var(--sent-muted);
}

.order-card__body {
  display: flex;
  align-items: stretch;
  gap: 16px;
  padding: 14px 16px;
  cursor: pointer;
}
.order-items {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.order-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.order-item__thumb {
  width: 68px;
  height: 68px;
  flex: none;
  border-radius: 8px;
}
.order-item__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.order-item__title {
  display: block;
  width: 100%;
  padding: 0;
  border: none;
  background: none;
  text-align: left;
  font-size: 14px;
  font-family: inherit;
  color: var(--sent-text);
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.order-item__title:hover {
  color: var(--sent-primary);
  text-decoration: underline;
}
.order-item__spec {
  font-size: 12px;
  color: var(--sent-muted);
}
.order-item__price {
  width: 92px;
  text-align: right;
  font-size: 13px;
  color: var(--sent-text-2);
}
.order-item__qty {
  width: 48px;
  text-align: right;
  font-size: 13px;
  color: var(--sent-muted);
}
.order-item--empty {
  font-size: 13px;
  color: var(--sent-muted);
}

.order-card__pay {
  width: 150px;
  flex: none;
  border-left: 1px dashed #eef0f2;
  padding-left: 16px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  text-align: right;
}
.order-card__pay-label {
  font-size: 12px;
  color: var(--sent-muted);
}
.order-card__pay-amount {
  font-size: 18px;
  color: var(--sent-accent-dark);
}

.order-card__ops {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 16px 14px;
  border-top: 1px dashed #f2f3f5;
}

.list-hint {
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}
.list-page {
  justify-content: center;
  padding: 24px 0 10px;
}
</style>
