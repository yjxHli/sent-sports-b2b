<template>
  <el-tag :type="tone" size="small" effect="light">{{ text }}</el-tag>
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

const TONES: Record<StatusTone, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
  primary: 'primary',
  success: 'success',
  warning: 'warning',
  danger: 'danger',
  info: 'info',
};
const tone = computed(() => TONES[props.tone ?? statusTone(props.status)]);
</script>
