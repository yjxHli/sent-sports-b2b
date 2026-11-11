<template>
  <el-card class="stub-card">
    <template #header>
      <span class="stub-title">{{ title }}</span>
      <el-tag v-if="perm" size="small" type="info" class="stub-perm">{{ perm }}</el-tag>
    </template>
    <el-empty :description="`「${title}」业务页面待对应 P6 任务实现`" :image-size="90" />
    <p class="stub-note">路由、权限守卫与菜单已按静态路由表生效；此处仅作为联调占位。</p>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const title = computed(() => (route.meta.title as string) || '业务页面');
const perm = computed(() => (route.meta.perm as string) || '');
</script>

<style scoped>
.stub-card { min-height: 360px; }
.stub-title { font-weight: 600; }
.stub-perm { margin-left: 10px; }
.stub-note { text-align: center; font-size: 12px; color: #a4b0ab; }
</style>
