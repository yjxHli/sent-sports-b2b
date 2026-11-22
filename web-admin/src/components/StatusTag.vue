<template>
  <!--
    统一状态标签（2026-11-21 设计重构）：
    浅底 Tag 在一张 18 行的表里就是 18 个色块，噪音大于信息；
    改成「6px 色点 + 灰色文字」，颜色只承载语义，文字只承载状态名。
    空状态不再渲染标签框（此前 statusLabel 返回 '-'，会渲染出一个空灰框）。
  -->
  <span v-if="text && text !== '-'" class="sent-status" :class="`sent-status--${toneKey}`">
    <i class="sent-status__dot" aria-hidden="true" />
    <span class="sent-status__text">{{ text }}</span>
  </span>
  <span v-else class="sent-status__none">-</span>
</template>

<script setup lang="ts">
/**
 * 统一状态标签（plan §9.4.5）：颜色与文案全部取自 @sent/shared 的同源映射，
 * 页面禁止自己 if/else 配色，避免订单/支付/定制/售后四套状态各写一份。
 */
import { computed } from 'vue';
import { statusLabel, statusTone, type StatusTone } from '@sent/shared';

const props = defineProps<{
  status?: string | null;
  /** 覆盖文案（如「已上架」这类非枚举展示） */
  label?: string;
  /** 非枚举展示时显式指定语义色 */
  tone?: StatusTone;
}>();

const text = computed(() => props.label ?? statusLabel(props.status));

/** 色点只有 5 个语义色，直接映射到令牌，不再借 Element 的 tag type */
const toneKey = computed<StatusTone>(() => props.tone ?? statusTone(props.status));
</script>

<style scoped>
.sent-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  line-height: 1;
  color: var(--sent-ink-2);
  white-space: nowrap;
}

.sent-status__dot {
  flex: none;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sent-ink-3);
}

.sent-status--primary .sent-status__dot {
  background: var(--sent-primary);
}
.sent-status--success .sent-status__dot {
  background: var(--sent-ok);
}
.sent-status--warning .sent-status__dot {
  background: var(--sent-warn);
}
.sent-status--danger .sent-status__dot {
  background: var(--sent-danger);
}
.sent-status--info .sent-status__dot {
  background: var(--sent-ink-3);
}

/* 空值：一个弱色破折号，没有框、没有底色 */
.sent-status__none {
  font-size: 13px;
  color: #b3bdb7;
}
</style>
