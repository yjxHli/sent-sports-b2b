<template>
  <div class="error-page">
    <div class="error-code">404</div>
    <h2>页面不存在</h2>
    <p>请检查地址是否正确，或返回工作台继续操作。</p>
    <el-button type="primary" @click="goHome">返回工作台</el-button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';

const router = useRouter();
function goHome(): void {
  void router.replace('/dashboard');
}
</script>

<style scoped>
.error-page { padding: 80px 20px; text-align: center; color: #1f2d28; }
.error-code { font-size: 76px; font-weight: 700; color: #12664f; line-height: 1; }
.error-page h2 { margin: 18px 0 8px; font-size: 20px; }
.error-page p { margin: 0 0 24px; color: #7b8a84; }
</style>
