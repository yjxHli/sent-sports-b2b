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
.error-page {
  padding: 80px 24px;
  max-width: 420px;
  color: var(--sent-ink);
}
.error-code {
  font-size: 40px;
  font-weight: 500;
  line-height: 1;
  letter-spacing: -0.02em;
  color: var(--sent-ink-3);
}
.error-page h2 {
  margin: 16px 0 8px;
  font-size: 20px;
  font-weight: 600;
}
.error-page p {
  margin: 0 0 24px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--sent-ink-3);
}
</style>
