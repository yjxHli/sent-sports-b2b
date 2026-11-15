<template>
  <div class="chat-order" :class="{ 'chat-order--self': self }">
    <template v-if="card">
      <div class="chat-order__head">
        <span class="chat-order__tag">订单</span>
        <b class="chat-order__no">{{ card.orderNo }}</b>
      </div>
      <div class="chat-order__title">{{ card.title || '订单商品' }}</div>
      <div class="chat-order__foot">
        <span class="chat-order__status">{{ statusLabel(card.status) }}</span>
        <span class="chat-order__amount">¥{{ fmtCent(card.payableCent ?? 0) }}</span>
      </div>
      <button type="button" class="chat-order__btn" @click="openOrder">查看订单详情 ›</button>
    </template>
    <!-- 解析失败（旧数据 / 手工改库）：退回纯文本，别让整个消息区白屏 -->
    <div v-else class="chat-order__plain">{{ content }}</div>
  </div>
</template>

<script setup lang="ts">
/**
 * 聊天里的「订单卡片」气泡（2026-11-15）。
 *
 * 消息表只存订单摘要 JSON（orderNo / status / payableCent / title），不存订单快照本体：
 * 订单会改状态、会退款，卡片上的价格只用于「认出是哪一单」，真实信息一律点进详情页看。
 * 用户端客服页与客服工作台各有一份（跳转落点不同：/order/:no vs /order/detail/:no）。
 */
import { computed } from 'vue';
import { useRouter } from 'vue-router';
import { fmtCent, statusLabel } from '@sent/shared';

interface ChatOrderPayload {
  orderNo?: string;
  status?: string;
  payableCent?: number;
  title?: string;
  createdAt?: number;
}

const props = withDefaults(defineProps<{ content: string; self?: boolean }>(), { self: false });
const router = useRouter();

const card = computed<ChatOrderPayload | null>(() => {
  try {
    const parsed = JSON.parse(props.content) as ChatOrderPayload;
    return parsed && typeof parsed.orderNo === 'string' && parsed.orderNo ? parsed : null;
  } catch {
    return null;
  }
});

function openOrder(): void {
  const no = card.value?.orderNo;
  if (!no) return;
  router.push(`/order/${no}`);
}
</script>

<style scoped>
.chat-order {
  max-width: 268px;
  background: #fff;
  border: 1px solid #e6ece9;
  border-radius: 10px;
  padding: 10px 12px 8px;
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
  margin-bottom: 8px;
}
.chat-order__tag {
  flex: none;
  font-size: 11px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 4px;
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
  font-weight: 600;
}
.chat-order__no {
  font-size: 12px;
  font-weight: 600;
  color: var(--sent-text);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-order__title {
  font-size: 13px;
  color: var(--sent-text-2);
  line-height: 1.45;
  margin-bottom: 6px;
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
  color: var(--sent-primary);
}
.chat-order__amount {
  font-size: 14px;
  font-weight: 700;
  color: var(--sent-accent-dark);
}
.chat-order__btn {
  margin-top: 6px;
  width: 100%;
  border: none;
  border-radius: 6px;
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
  padding: 6px 0;
  cursor: pointer;
}
.chat-order__btn:hover {
  background: var(--sent-primary);
  color: #fff;
}
.chat-order__plain {
  font-size: 13px;
  color: var(--sent-text-2);
  word-break: break-all;
}
</style>
