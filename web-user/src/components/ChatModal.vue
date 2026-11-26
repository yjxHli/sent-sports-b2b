<template>
  <!--
    聊天页内的浮层基座（2026-11-24）。
    手写遮罩而不是 el-dialog：本页多个弹窗要求「点弹窗以外任意位置即回到聊天界面」，
    遮罩自身必须能被点到并关闭，el-dialog 的 overlay 与内容分属两层，点击时序不好控。
    data-testid 挂在遮罩上，E2E 点 (4,4) 即命中遮罩。
  -->
  <div v-if="open" class="chat-modal" :data-testid="testid" @click.self="emit('close')">
    <div
      class="chat-modal__panel"
      :class="panelClass"
      :style="{ width }"
      role="dialog"
      aria-modal="true"
    >
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  open: boolean;
  /** 遮罩上的 data-testid，E2E 用它定位整个浮层 */
  testid: string;
  panelClass?: string;
  /** 面板宽度（默认 620px）：历史会话要左右分栏，得宽一点 */
  width?: string;
}>(), { panelClass: '', width: '620px' });

const emit = defineEmits<{ close: [] }>();
</script>

<style scoped>
.chat-modal {
  position: fixed;
  inset: 0;
  z-index: 2100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(17, 24, 39, 0.45);
  animation: chat-modal-fade 0.16s ease-out;
}
.chat-modal__panel {
  display: flex;
  flex-direction: column;
  width: 620px;
  max-width: 100%;
  max-height: min(78vh, 640px);
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 18px 48px rgba(18, 102, 79, 0.22);
  overflow: hidden;
  animation: chat-modal-pop 0.18s ease-out;
}
@keyframes chat-modal-fade {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes chat-modal-pop {
  from { opacity: 0; transform: translateY(8px) scale(0.985); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
@media (max-width: 720px) {
  .chat-modal {
    padding: 12px;
  }
  .chat-modal__panel {
    max-height: 86vh;
  }
}
</style>
