<template>
  <!-- 商城端薄壳：共享卡片只管展示与事件，跳详情页由本端决定 -->
  <SharedChatOrderCard
    :content="content"
    :align="self ? 'self' : 'other'"
    @open-detail="openOrder"
  />
</template>

<script setup lang="ts">
/**
 * 聊天订单卡片（2026-11-28 P2：展示部分上提到 @sent/shared/components/ChatOrderCard.vue）。
 * 本端行为：点卡片跳商城订单详情 /order/:no，客服接待时点卡片在当面弹窗。
 */
import { useRouter } from 'vue-router';
import SharedChatOrderCard from '@sent/shared/components/ChatOrderCard.vue';

withDefaults(defineProps<{ content: string; self?: boolean }>(), { self: false });
const router = useRouter();

function openOrder(orderNo: string): void {
  void router.push(`/order/${orderNo}`);
}
</script>
