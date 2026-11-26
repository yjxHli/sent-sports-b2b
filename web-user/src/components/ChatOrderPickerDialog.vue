<template>
  <ChatModal
    :open="open"
    testid="chat-order-dialog"
    panel-class="order-dlg"
    @close="close"
  >
    <header class="order-dlg__head">
      <div class="order-dlg__title">
        <b>发订单给客服</b>
        <span>选中一单，客服能直接看到进度和商品，不用再复述</span>
      </div>
      <button type="button" class="order-dlg__close" title="关闭" @click="close">×</button>
    </header>

    <!-- 双 Tab：订单问题（选单）/ 非订单问题（常见问题直发） -->
    <div class="order-dlg__tabs">
      <button
        type="button"
        class="order-dlg__tab"
        data-testid="order-tab-order"
        :class="{ 'is-active': tab === 'order' }"
        @click="tab = 'order'"
      >订单问题</button>
      <button
        type="button"
        class="order-dlg__tab"
        data-testid="order-tab-other"
        :class="{ 'is-active': tab === 'other' }"
        @click="tab = 'other'"
      >非订单问题</button>
    </div>

    <div class="order-dlg__body">
      <template v-if="tab === 'order'">
        <div v-if="loading" class="order-dlg__state">加载中…</div>
        <div v-else-if="orders.length === 0" class="order-dlg__state">
          还没有订单，去逛逛吧
        </div>
        <ul v-else class="order-dlg__list">
          <li
            v-for="o in orders"
            :key="o.orderNo"
            class="order-dlg__row"
            data-testid="order-dialog-row"
          >
            <div class="order-dlg__row-top">
              <span class="order-dlg__no">{{ o.orderNo }}</span>
              <span class="order-dlg__status">{{ statusLabel(o.status) }}</span>
            </div>
            <div class="order-dlg__goods">{{ firstTitle(o) }}</div>
            <div class="order-dlg__row-foot">
              <span class="order-dlg__amount">¥{{ fmtCent(Number(o.payableCent ?? 0)) }}</span>
              <button
                type="button"
                class="order-dlg__send"
                :disabled="disabled || sendingOrder === o.orderNo"
                :title="disabled ? '客服接入后可发送订单' : '发给客服'"
                @click="emit('send', o)"
              >{{ sendingOrder === o.orderNo ? '发送中…' : '发给客服' }}</button>
            </div>
          </li>
        </ul>
      </template>

      <ul v-else class="order-dlg__faq">
        <li v-for="q in FAQ_LIST" :key="q">
          <button
            type="button"
            class="order-dlg__faq-btn"
            data-testid="faq-item"
            @click="emit('faq', q)"
          >
            <span class="order-dlg__faq-dot">?</span>
            <span class="order-dlg__faq-text">{{ q }}</span>
            <span class="order-dlg__faq-arrow">›</span>
          </button>
        </li>
      </ul>
    </div>

    <footer class="order-dlg__foot">
      <span>发送后客服会看到同一张订单卡片</span>
      <router-link class="order-dlg__all" to="/orders">查看全部订单 ›</router-link>
    </footer>
  </ChatModal>
</template>

<script setup lang="ts">
/**
 * 「订单 / 非订单问题」选择弹窗（2026-11-24，对齐用户给的参考图）。
 *
 * 两个入口共用同一个弹窗：
 *   1) 输入区第一个按钮（📦 订单）；
 *   2) 聊天区底部「发订单给客服」横幅。
 * 坑位提醒：每次打开都要把 Tab 重置回「订单问题」，否则上一次停在 FAQ 会让人以为订单列表丢了。
 */
import { ref, watch } from 'vue';
import { fmtCent, statusLabel } from '@sent/shared';
import type { OrderListItem } from '@/api/order';
import ChatModal from './ChatModal.vue';

const props = withDefaults(defineProps<{
  open: boolean;
  orders: OrderListItem[];
  loading?: boolean;
  /** 正在发送的订单号：只禁用那一行，其余行还能点 */
  sendingOrder?: string;
  /** 排队中 / 已结束：整列按钮禁用（和输入框同口径） */
  disabled?: boolean;
}>(), { loading: false, sendingOrder: '', disabled: false });

const emit = defineEmits<{
  close: [];
  send: [order: OrderListItem];
  faq: [question: string];
}>();

/** 非订单问题：都是不需要订单号就能答的，点一下填进输入框 */
const FAQ_LIST = [
  '这件商品有现货吗？多久能发货？',
  '定制球衣印队徽，多少件起订？',
  '可以开增值税专用发票吗？',
  '收到的商品不合适，怎么退换？',
  '运费怎么算？满多少包邮？',
  '会员等级和积分怎么用？',
];

const tab = ref<'order' | 'other'>('order');

watch(() => props.open, (open) => {
  if (open) tab.value = 'order';
});

function close(): void {
  emit('close');
}

/** 首个商品名 + 件数：订单列表接口只给 items，卡片标题按这个口径拼 */
function firstTitle(o: OrderListItem): string {
  const items = o.items ?? [];
  const title = items[0]?.title || '订单商品';
  return items.length > 1 ? `${title} 等 ${items.length} 件` : title;
}
</script>

<style scoped>
.order-dlg__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 18px 12px;
  border-bottom: 1px solid #f2f4f5;
}
.order-dlg__title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.order-dlg__title b {
  font-size: 15px;
  color: var(--sent-text);
}
.order-dlg__title span {
  font-size: 12px;
  color: var(--sent-muted);
  line-height: 1.4;
}
.order-dlg__close {
  margin-left: auto;
  border: none;
  background: transparent;
  font-size: 20px;
  line-height: 1;
  color: var(--sent-muted);
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 6px;
}
.order-dlg__close:hover {
  color: var(--sent-primary);
  background: var(--sent-primary-soft);
}

.order-dlg__tabs {
  display: flex;
  gap: 6px;
  padding: 10px 18px 0;
}
.order-dlg__tab {
  border: 1px solid transparent;
  background: #f5f7f8;
  color: var(--sent-text-2);
  font-family: inherit;
  font-size: 13px;
  line-height: 1;
  padding: 8px 16px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.15s;
}
.order-dlg__tab:hover {
  color: var(--sent-primary);
}
.order-dlg__tab.is-active {
  background: var(--sent-primary-soft);
  border-color: var(--sent-primary);
  color: var(--sent-primary);
  font-weight: 600;
}

.order-dlg__body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 18px 12px;
}
.order-dlg__state {
  padding: 32px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}
.order-dlg__list,
.order-dlg__faq {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.order-dlg__row {
  border: 1px solid #edf0f2;
  border-radius: 10px;
  padding: 10px 12px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.order-dlg__row:hover {
  border-color: var(--sent-primary);
  box-shadow: 0 4px 12px rgba(18, 102, 79, 0.08);
}
.order-dlg__row-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.order-dlg__no {
  font-size: 11px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: var(--sent-muted);
}
.order-dlg__status {
  flex: none;
  font-size: 11px;
  color: var(--sent-primary);
}
.order-dlg__goods {
  margin: 6px 0 8px;
  font-size: 13px;
  line-height: 1.45;
  color: var(--sent-text);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.order-dlg__row-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.order-dlg__amount {
  font-size: 14px;
  font-weight: 700;
  color: var(--sent-accent-dark);
}
.order-dlg__send {
  border: 1px solid var(--sent-primary);
  background: #fff;
  color: var(--sent-primary);
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
}
.order-dlg__send:hover:not(:disabled) {
  background: var(--sent-primary);
  color: #fff;
}
.order-dlg__send:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.order-dlg__faq-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  text-align: left;
  padding: 11px 12px;
  border: 1px solid #f3d8c8;
  border-radius: 10px;
  background: #fffaf6;
  color: #8a5a3b;
  font-family: inherit;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.order-dlg__faq-btn:hover {
  border-color: var(--sent-accent);
  background: var(--sent-accent-soft);
  color: var(--sent-accent-dark);
}
.order-dlg__faq-dot {
  width: 18px;
  height: 18px;
  flex: none;
  border-radius: 50%;
  background: var(--sent-accent);
  color: #fff;
  font-size: 11px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.order-dlg__faq-text {
  flex: 1;
}
.order-dlg__faq-arrow {
  color: #d9b49b;
}

.order-dlg__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 18px;
  border-top: 1px solid #f2f4f5;
  font-size: 11px;
  color: var(--sent-muted);
}
.order-dlg__all {
  color: var(--sent-primary);
  font-size: 12px;
}
</style>
