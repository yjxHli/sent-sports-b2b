<template>
  <!-- 个人中心侧栏图标（2026-11-27）：统一 24 格描边图标，颜色随 currentColor。
       原先用的是 emoji，同一列里色彩/粗细/风格各不相同（彩色房子 + 灰色推车 + 蓝色箭头），
       放大后非常显脏；这里换成一套线条图标，选中态自动跟随文字变绿。
       视觉尺寸 15px（比 14px 文字略小，符合「图标略小于文字」）。 -->
  <svg
    class="nav-icon"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.8"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <template v-if="name === 'home'">
      <path d="M3.6 10.7 12 4l8.4 6.7V20a.8.8 0 0 1-.8.8h-4.8v-6.2H9.2v6.2H4.4a.8.8 0 0 1-.8-.8z" />
    </template>
    <template v-else-if="name === 'cart'">
      <path d="M3 4.2h2.1l2.3 10.6h10l2-7.6H6.2" />
      <circle cx="9.4" cy="19" r="1.5" />
      <circle cx="17.2" cy="19" r="1.5" />
    </template>
    <template v-else-if="name === 'orders'">
      <path d="M4 8.2 12 4l8 4.2v7.6L12 20l-8-4.2z" />
      <path d="M4 8.2 12 12.4l8-4.2" />
      <path d="M12 12.4V20" />
    </template>
    <template v-else-if="name === 'coupon'">
      <path d="M4 8.4a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v2.1a1.7 1.7 0 0 0 0 3.4v2.1a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-2.1a1.7 1.7 0 0 0 0-3.4z" />
    </template>
    <template v-else-if="name === 'trace'">
      <path d="M6.6 4h10.8a.8.8 0 0 1 .8.8V20l-6.2-4-6.2 4V4.8a.8.8 0 0 1 .8-.8z" />
    </template>
    <template v-else-if="name === 'invoice'">
      <path d="M6.2 3h8.4l3.2 3.2V21H6.2z" />
      <path d="M9.2 9.2h5.6M9.2 13h5.6M9.2 16.6h3.6" />
    </template>
    <template v-else-if="name === 'refund'">
      <path d="M9.2 6.8 4.8 11.2 9.2 15.6" />
      <path d="M4.8 11.2h9.4a5 5 0 0 1 5 5v.6" />
    </template>
    <template v-else-if="name === 'member'">
      <path d="M4.2 17.4h15.6l-1.3-9.1-4.4 3.1L12 5.6 9.9 11.4 5.5 8.3z" />
      <path d="M4.2 20.2h15.6" />
    </template>
    <template v-else-if="name === 'reviews'">
      <path d="M12 4.4l2.4 4.9 5.4.8-3.9 3.8.9 5.4L12 16.8l-4.8 2.5.9-5.4-3.9-3.8 5.4-.8z" />
    </template>
    <template v-else-if="name === 'enterprise'">
      <path d="M5 21V4.8a.8.8 0 0 1 .8-.8h8.4a.8.8 0 0 1 .8.8V21" />
      <path d="M15 10.2h3.2a.8.8 0 0 1 .8.8V21" />
      <path d="M3.4 21h17.2" />
      <path d="M8.2 8h3M8.2 12h3M8.2 16h3" />
    </template>
  </svg>
</template>

<script setup lang="ts">
defineProps<{ name: string }>();
</script>

<style scoped>
.nav-icon {
  width: 15px;
  height: 15px;
  display: block;
  flex: none;
}
</style>
