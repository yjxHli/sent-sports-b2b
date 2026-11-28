<template>
  <!--
    聊天里的订单摘要卡片（2026-11-28 P2 批次上提到 shared）。
    align 只控制「自己发的 / 对方发的」外观；点击行为交给使用方通过 open-detail 事件决定：
    商城端跳 /order/:no，客服台在当面弹窗——这个差异不该写进共享组件。
  -->
  <div class="chat-order" :class="{ 'chat-order--self': align === 'self', 'chat-order--mine': align === 'self' }">
    <template v-if="card()">
      <div class="chat-order__head">
        <span class="chat-order__tag">订单</span>
        <b class="chat-order__no">{{ card()?.orderNo }}</b>
      </div>
      <div class="chat-order__title">{{ card()?.title || '订单商品' }}</div>
      <div class="chat-order__foot">
        <span class="chat-order__status">{{ statusLabel(card()?.status) }}</span>
        <span class="chat-order__amount">¥{{ fmtCent(card()?.payableCent ?? 0) }}</span>
      </div>
      <button type="button" class="chat-order__btn" @click="openOrder">查看订单详情 ›</button>
    </template>
    <!-- 解析失败（旧数据 / 手工改库）：退回纯文本，别让整个消息区白屏 -->
    <div v-else class="chat-order__plain">{{ content }}</div>
  </div>
</template>

<script setup lang="ts">
/**
 * 消息表只存订单摘要 JSON（orderNo / status / payableCent / title），不存订单快照本体：
 * 订单会改状态、会退款，卡片上的价格只用于「认出是哪一单」，真实信息一律点进详情页看。
 *
 * <p>刻意不 import vue：defineProps / defineEmits 是编译宏，解析函数用普通函数即可，
 * 这样共享包不必为了一个展示组件引入 UI 框架依赖。</p>
 */
import { fmtCent } from '../format';
import { statusLabel } from '../enums';

interface ChatOrderPayload {
  orderNo?: string;
  status?: string;
  payableCent?: number;
  title?: string;
  createdAt?: number;
}

const props = withDefaults(defineProps<{
  content: string;
  /** self = 自己发的气泡；other = 对方发的气泡 */
  align?: 'self' | 'other';
}>(), { align: 'other' });

const emit = defineEmits<{ (e: 'open-detail', orderNo: string): void }>();

function card(): ChatOrderPayload | null {
  try {
    const parsed = JSON.parse(props.content) as ChatOrderPayload;
    return parsed && typeof parsed.orderNo === 'string' && parsed.orderNo ? parsed : null;
  } catch {
    return null;
  }
}

function openOrder(): void {
  const no = card()?.orderNo;
  if (no) emit('open-detail', no);
}
</script>

<style scoped>
.chat-order {
  display: inline-flex;
  flex-direction: column;
  gap: 6px;
  max-width: 268px;
  padding: 10px 12px 8px;
  border: 1px solid #e6ece9;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.chat-order--self {
  border-color: #cfe3da;
  background: #fbfefc;
}
.chat-order__head {
  display: flex;
  align-items: center;
  gap: 6px;
}
.chat-order__tag {
  flex: none;
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 4px;
  background: var(--sent-primary-soft, #e7f1ec);
  color: var(--sent-primary, #12664f);
  font-weight: 600;
}
.chat-order__no {
  font-size: 12px;
  font-weight: 600;
  color: var(--sent-text, #1f2329);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-order__title {
  font-size: 13px;
  color: var(--sent-text-2, #4b5563);
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.chat-order__foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding-top: 6px;
  border-top: 1px dashed #eef0f2;
}
.chat-order__status {
  font-size: 12px;
  color: var(--sent-primary, #12664f);
}
.chat-order__amount {
  font-size: 14px;
  font-weight: 700;
  color: var(--sent-accent-dark, #d46b08);
}
.chat-order__btn {
  border: none;
  border-radius: 6px;
  background: var(--sent-primary-soft, #e7f1ec);
  color: var(--sent-primary, #12664f);
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
  padding: 6px 0;
  cursor: pointer;
}
.chat-order__btn:hover {
  background: var(--sent-primary, #12664f);
  color: #fff;
}
.chat-order__plain {
  font-size: 13px;
  color: var(--sent-text-2, #4b5563);
  word-break: break-all;
}
</style>
