<template>
  <div class="nf-wrap">
    <div class="nf-code">404</div>
    <p>页面不存在</p>
    <el-button type="primary" @click="$router.replace('/home')">返回首页</el-button>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.nf-wrap {
  max-width: 480px;
  margin: 0 auto;
  padding-top: 30vh;
  text-align: center;
  color: #8a8f99;
}
.nf-code {
  font-size: 48px;
  font-weight: 700;
  color: var(--sent-primary);
}
</style>
