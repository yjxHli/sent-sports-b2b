<template>
  <span class="chat-order" :class="{ 'chat-order--mine': mine }">
    <template v-if="card">
      <span class="chat-order__head">
        <span class="chat-order__tag">订单</span>
        <b class="chat-order__no">{{ card.orderNo }}</b>
      </span>
      <span class="chat-order__title">{{ card.title || '订单商品' }}</span>
      <span class="chat-order__foot">
        <span class="chat-order__status">{{ statusLabel(card.status) }}</span>
        <span class="chat-order__amount">¥{{ fmtCent(card.payableCent ?? 0) }}</span>
      </span>
      <button type="button" class="chat-order__btn" @click="openOrder">查看订单详情 ›</button>
    </template>
    <!-- 解析失败（旧数据）：退回纯文本，不让消息区白屏 -->
    <span v-else class="chat-order__plain">{{ content }}</span>
  </span>
</template>

<script setup lang="ts">
/**
 * 客服工作台里的「订单卡片」气泡。
 *
 * 与用户端同名组件是两份实现：跳转落点不同（管理端 `/order/detail/:orderNo`）。
 * 解析口径一致：content 是订单摘要 JSON，只用于识别是哪一单，金额状态以详情页为准。
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

const props = withDefaults(defineProps<{ content: string; mine?: boolean }>(), { mine: false });
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
  router.push(`/order/detail/${no}`);
}
</script>

<style scoped>
.chat-order {
  display: inline-flex;
  flex-direction: column;
  gap: 6px;
  max-width: 260px;
  padding: 10px 12px 8px;
  border: 1px solid #e1e9e5;
  border-radius: 8px;
  background: #fff;
}
.chat-order--mine {
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
  background: #e8f3ef;
  color: #12664f;
  font-weight: 600;
}
.chat-order__no {
  font-size: 12px;
  color: #1d2b26;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chat-order__title {
  font-size: 13px;
  line-height: 1.45;
  color: #4a5b54;
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
  gap: 10px;
  padding-top: 6px;
  border-top: 1px dashed #eef0f2;
}
.chat-order__status {
  font-size: 12px;
  color: #12664f;
}
.chat-order__amount {
  font-size: 14px;
  font-weight: 700;
  color: #d46b08;
}
.chat-order__btn {
  border: none;
  border-radius: 6px;
  background: #f0f7f4;
  color: #12664f;
  font-family: inherit;
  font-size: 12px;
  line-height: 1;
  padding: 6px 0;
  cursor: pointer;
}
.chat-order__btn:hover {
  background: #12664f;
  color: #fff;
}
.chat-order__plain {
  font-size: 13px;
  color: #4a5b54;
  word-break: break-all;
}
</style>
