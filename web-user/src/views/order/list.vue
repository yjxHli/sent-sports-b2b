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
      <div class="order-search">
        <el-input
          v-model="keywordInput"
          class="order-search__input"
          placeholder="搜索订单号 / 商品名称"
          clearable
          @keyup.enter="applySearch"
        />
        <el-button type="primary" @click="applySearch">搜索</el-button>
      </div>
      <el-button
        class="order-batch-toggle"
        size="small"
        :type="manage ? 'primary' : 'default'"
        data-testid="order-batch-toggle"
        @click="toggleManage"
      >
        {{ manage ? '退出批量' : '批量管理' }}
      </el-button>
      <span class="order-tabs__total">共 {{ total }} 笔</span>
    </div>

    <!-- 批量操作条：只在批量管理态出现，提示「只有终态单能删」，避免用户勾了在途单又困惑 -->
    <div v-if="manage" class="batch-bar" data-testid="order-batch-bar">
      <el-checkbox
        :model-value="allSelected"
        :indeterminate="indeterminate"
        :disabled="selectableOrders.length === 0 || selectingAll"
        data-testid="order-batch-all"
        @change="toggleAll"
      >
        全选全部
      </el-checkbox>
      <span class="batch-bar__hint">
        已选 <b>{{ selected.length }}</b> 笔 · 仅「已完成 / 已取消 / 已关闭」可删除，在途订单请先收货或取消
      </span>
      <el-button
        type="danger"
        size="small"
        :disabled="selected.length === 0"
        :loading="removing"
        data-testid="order-batch-delete"
        @click="removeSelected"
      >
        删除所选
      </el-button>
    </div>

    <div v-if="loading" class="list-hint">加载中…</div>
    <el-empty v-else-if="orders.length === 0" :description="emptyText" />

    <template v-else>
      <div class="order-list">
        <div v-for="o in orders" :key="o.orderNo" class="order-card" :class="{ 'order-card--selected': isSelected(o.orderNo) }">
          <!-- 卡片头：下单时间 / 订单号 / 店铺 / 状态 -->
          <div class="order-card__head">
            <el-checkbox
              v-if="manage"
              class="order-card__check"
              :model-value="isSelected(o.orderNo)"
              :disabled="!canDelete(o)"
              :title="canDelete(o) ? '选择这一笔' : '在途订单不能删除，请先完成收货或取消订单'"
              data-testid="order-select"
              @change="toggle(o.orderNo)"
            />
            <span class="order-card__time">{{ fmtDateTime(o.createdAt) }}</span>
            <span class="order-card__no">订单号：{{ o.orderNo }}</span>
            <span class="order-card__shop">森特体育自营店</span>
            <span class="order-card__status" :class="`order-card__status--${toneOf(o.status)}`">
              {{ label(o.status) }}
            </span>
          </div>

          <!-- 卡片体：商品行 + 实付款 -->
          <div class="order-card__body" @click="onCardClick(o)">
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
            <!-- 待付款 5 分钟倒计时：与详情页同一时间源（后端 expire_at），超时由后端关单 -->
            <span v-if="payCountdownOf(o)" class="order-card__countdown" data-testid="pay-countdown">
              剩余支付时间 <b>{{ payCountdownOf(o) }}</b>
            </span>
            <el-button size="small" @click="goDetail(o.orderNo)">查看详情</el-button>
            <el-button
              v-if="canInvoiceOrder(o)"
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
        v-if="total > 0"
        class="list-page"
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :page-size="size"
        :page-sizes="[10, 15, 20, 50, 100]"
        :current-page="page"
        @current-change="onPage"
        @size-change="onSize"
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
 * 状态筛选与关键字搜索都由后端下推（`GET /api/orders?status=&keyword=`），前端不做整表过滤，
 * 否则分页 total 与每页条数都会失真。搜索词与状态一起写回 URL，刷新/分享不丢。
 */
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { fmtDateTime, statusLabel, statusTone, type StatusTone } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { orderApi, type OrderItemLine, type OrderListItem } from '@/api/order';
import ProductThumb from '@/components/ProductThumb.vue';
import InvoiceApplyDialog from '@/components/InvoiceApplyDialog.vue';
import { canInvoiceOrder } from '@/composables/useOrderActions';

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
/** 搜索框的输入值（可编辑）与已生效的搜索词（发请求用），避免输入中就反复请求 */
const keywordInput = ref<string>((route.query.keyword as string) || '');
const keyword = ref<string>(keywordInput.value);

/** 开票弹窗（列表里直接开票，不必先进详情） */
const invoiceVisible = ref(false);
const invoiceOrderNo = ref('');
const invoicePayableCent = ref(0);

const emptyText = computed(() => {
  if (keyword.value) return `没有找到与「${keyword.value}」相关的订单`;
  return activeTab.value ? '该状态下暂无订单' : '还没有订单，去逛逛吧';
});

/* ---------------- 批量删除（多选） ---------------- */
/**
 * 「可以选择删除哪些」= 每张卡片一个复选框 + 全选。
 * 可删范围与后端 `DeleteOrderUseCase.DELETABLE_STATUS` 同口径（已完成 / 已取消 / 已关闭）：
 * 在途单（待付款/待发货/待收货）删了客服就找不到单，复选框直接禁用并把原因写在 title 上，
 * 而不是等用户勾完再报错。真正的归属与状态校验仍在服务端（前端只是减噪）。
 */
const DELETABLE_STATUS = ['COMPLETED', 'CANCELLED', 'CLOSED'];
const manage = ref(false);
/** 已勾选的订单号（可跨页；全选时是当前筛选下的全部可删订单） */
const selected = ref<string[]>([]);
const removing = ref(false);
/** 跨页全选正在拉全量，避免连点重复请求 */
const selectingAll = ref(false);

function canDelete(o: OrderListItem): boolean {
  return DELETABLE_STATUS.includes(String(o.status ?? ''));
}

const selectableOrders = computed(() => orders.value.filter(canDelete));
/** 全选态：当前页可删的单都在选择集里；跨页时选择集更大，同样算「已全选」 */
const allSelected = computed(
  () => selectableOrders.value.length > 0
    && selectableOrders.value.every((o) => selected.value.includes(o.orderNo)),
);
/** 半选态：勾了一部分，全选框显示横线而不是勾 */
const indeterminate = computed(() => selected.value.length > 0 && !allSelected.value);

function isSelected(orderNo: string): boolean {
  return selected.value.includes(orderNo);
}

function toggleManage(): void {
  manage.value = !manage.value;
  selected.value = [];
}

function toggle(orderNo: string): void {
  selected.value = isSelected(orderNo)
    ? selected.value.filter((x) => x !== orderNo)
    : [...selected.value, orderNo];
}

/**
 * 全选/取消全选（2026-11-26 用户要求）：全选 = 当前筛选下「所有可删订单」，不是本页。
 * 列表是后端分页的，只能按同样的筛选条件循环拉全量；在途单永远进不了选择集。
 */
async function toggleAll(value: boolean | string | number): Promise<void> {
  if (!value) {
    selected.value = [];
    return;
  }
  if (selectingAll.value) return;
  selectingAll.value = true;
  try {
    const pageSize = 100;
    const all: OrderListItem[] = [];
    for (let p = 1; p <= 50; p += 1) {
      const res = await unwrap(orderApi.list({
        page: p,
        size: pageSize,
        status: activeTab.value || null,
        keyword: keyword.value || null,
      }));
      const list = (res.list ?? []) as OrderListItem[];
      all.push(...list);
      if (list.length < pageSize || all.length >= Number(res.total ?? 0)) break;
    }
    selected.value = all.filter(canDelete).map((o) => o.orderNo);
  } catch {
    ElMessage.error('加载全部订单失败，请重试');
  } finally {
    selectingAll.value = false;
  }
}

/** 批量态点卡片 = 勾选；普通态点卡片 = 进详情（保持原交互不变） */
function onCardClick(o: OrderListItem): void {
  if (!manage.value) {
    goDetail(o.orderNo);
    return;
  }
  if (!canDelete(o)) {
    ElMessage.warning('在途订单不能删除，请先确认收货或取消订单');
    return;
  }
  toggle(o.orderNo);
}

/**
 * 删除所选：二次确认 → 调批量接口 → 按「删了几笔 / 几笔没删掉及原因」分别提示。
 * 后端不会因为一单在途就整批失败，所以这里也不能把 skipped 当错误吞掉。
 */
async function removeSelected(): Promise<void> {
  if (selected.value.length === 0) return;
  const count = selected.value.length;
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${count} 笔订单吗？删除后将从「我的订单」中移除，客服与财务侧仍可查询原始记录。`,
      '删除订单',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '再想想' },
    );
  } catch {
    return;
  }

  removing.value = true;
  try {
    const res = await unwrap(orderApi.batchRemove([...selected.value]));
    const deleted = Number(res?.deleted ?? 0);
    const skipped = res?.skipped ?? [];
    if (deleted > 0) ElMessage.success(`已删除 ${deleted} 笔订单`);
    if (skipped.length > 0) {
      const detail = skipped
        .slice(0, 3)
        .map((s) => `${s.id}：${s.reason}`)
        .join('；');
      ElMessage.warning(
        `${skipped.length} 笔未删除 —— ${detail}${skipped.length > 3 ? ' 等' : ''}`,
      );
    }
    if (deleted === 0 && skipped.length === 0) ElMessage.info('所选订单已删除，无需重复操作');
    selected.value = [];
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message || '删除失败');
  } finally {
    removing.value = false;
  }
}

function label(s: string | null): string { return statusLabel(s); }
function toneOf(s: string | null): StatusTone { return statusTone(s); }

/* ---------------- 待付款 5 分钟倒计时 ---------------- */
/**
 * 时间源是后端 {@code orders.expire_at}（列表接口回 {@code expireAtMillis}），
 * 与 {@code OrderTimeoutJob} 判超时同一列：前端只做展示，不做关单。
 * 只有存在待付款单时才起 1s 定时器，其它 Tab 不空转。
 */
const nowTick = ref(Date.now());
let tickTimer: ReturnType<typeof setInterval> | undefined;

function stopTick(): void {
  if (tickTimer) clearInterval(tickTimer);
  tickTimer = undefined;
}

function syncTick(): void {
  const hasWaitPay = orders.value.some(
    (o) => o.status === 'WAIT_PAY' && Number(o.expireAtMillis ?? 0) > 0,
  );
  if (!hasWaitPay) {
    stopTick();
    return;
  }
  nowTick.value = Date.now();
  if (!tickTimer) tickTimer = setInterval(() => { nowTick.value = Date.now(); }, 1000);
}

/** 待付款剩余时间文本；已超时 / 非待付款返回空串（不渲染） */
function payCountdownOf(o: OrderListItem): string {
  if (o.status !== 'WAIT_PAY') return '';
  const expireAt = Number(o.expireAtMillis ?? 0);
  if (!expireAt) return '';
  const remain = expireAt - nowTick.value;
  if (remain <= 0) return '已超时';
  const total = Math.ceil(remain / 1000);
  return `${Math.floor(total / 60).toString().padStart(2, '0')}:${(total % 60).toString().padStart(2, '0')}`;
}

watch(orders, syncTick, { deep: false });
onUnmounted(stopTick);
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


function openInvoice(o: OrderListItem): void {
  invoiceOrderNo.value = o.orderNo;
  invoicePayableCent.value = Number(o.payableCent ?? 0);
  invoiceVisible.value = true;
}

/** 状态与搜索词一起写回 URL，切 Tab 不清空搜索，刷新后也能还原当前视图 */
function syncQuery(): void {
  const query: Record<string, string> = {};
  if (activeTab.value) query.status = activeTab.value;
  if (keyword.value) query.keyword = keyword.value;
  void router.replace({ path: '/orders', query });
}

function switchTab(key: string): void {
  if (activeTab.value === key) return;
  activeTab.value = key;
  page.value = 1;
  syncQuery();
  // 这里直接取数：watch 只在外部导航（浏览器前进/后退、直接改 URL）时兜底
  void load();
}

/** 点搜索或回车：trim 后重置到第一页再取数；清空即恢复全部 */
function applySearch(): void {
  const next = (keywordInput.value || '').trim();
  keyword.value = next;
  keywordInput.value = next;
  page.value = 1;
  syncQuery();
  void load();
}

async function onPage(p: number): Promise<void> {
  page.value = p;
  await load();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function onSize(s: number): Promise<void> {
  size.value = s;
  page.value = 1;
  await load();
}

/**
 * 「去支付」只做跳转：金额确认与真实支付都发生在独立支付页，
 * 不能在列表点一下就直接扣款并提示支付成功（用户 2026-11-18 反馈）。
 */
function pay(o: OrderListItem): void {
  void router.push(`/pay/${o.orderNo}`);
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
      keyword: keyword.value || null,
    }));
    orders.value = res.list ?? [];
    total.value = Number(res.total ?? 0);
    // 勾选集不随翻页裁剪：全选是「当前筛选下全部订单」，翻页只是换一屏看，
    // 裁掉上一页会把用户刚全选的几百单悄悄减成十几单。
  } finally {
    loading.value = false;
  }
}

watch(
  () => [route.query.status, route.query.keyword],
  ([status, kw]) => {
    const nextStatus = (status as string) || '';
    const nextKeyword = (kw as string) || '';
    if (nextStatus === activeTab.value && nextKeyword === keyword.value) return;
    activeTab.value = nextStatus;
    keyword.value = nextKeyword;
    keywordInput.value = nextKeyword;
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
.order-search {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 16px;
}
.order-search__input {
  width: 240px;
}
.order-batch-toggle {
  margin-left: 12px;
}
.order-tabs__total {
  margin-left: auto;
  font-size: 12px;
  color: var(--sent-muted);
}
@media (max-width: 900px) {
  .order-search__input {
    width: 160px;
  }
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
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 16px 14px;
  border-top: 1px dashed #f2f3f5;
}
/* 待付款倒计时：靠左，数字等宽避免每秒抖动 */
.order-card__countdown {
  margin-right: auto;
  font-size: 12px;
  color: #b8741a;
}
.order-card__countdown b {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  color: #d4380d;
}

/* ---------- 批量删除 ---------- */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border: 1px solid var(--sent-border);
  border-radius: var(--sent-radius);
  padding: 10px 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.batch-bar__hint {
  font-size: 12px;
  color: var(--sent-muted);
}
.batch-bar__hint b {
  color: var(--sent-accent-dark);
}
.batch-bar .el-button {
  margin-left: auto;
}
.order-card__check {
  margin-right: 4px;
  height: auto;
}
.order-card--selected {
  box-shadow: 0 0 0 2px var(--sent-primary-soft), 0 1px 3px rgba(0, 0, 0, 0.04);
}

.list-hint {
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}
</style>
