<template>
  <!-- 客服端薄壳：共享卡片只管展示与事件，工作台在当前页弹窗由调用方处理 -->
  <SharedChatOrderCard
    :content="content"
    :align="mine ? 'self' : 'other'"
    @open-detail="(no: string) => emit('open-detail', no)"
  />
</template>

<script setup lang="ts">
/**
 * 客服工作台订单卡片（2026-11-28 P2：展示部分上提到 shared）。
 * 本端行为不跳页：emit('open-detail', orderNo)，由工作台弹同一详情弹窗，接待过程不中断。
 */
import SharedChatOrderCard from '@sent/shared/components/ChatOrderCard.vue';

withDefaults(defineProps<{ content: string; mine?: boolean }>(), { mine: false });
const emit = defineEmits<{ (e: 'open-detail', orderNo: string): void }>();
</script>
