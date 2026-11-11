<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="login-brand">
        <span class="login-brand__mark">S</span>
        <div class="login-brand__text">
          <b>森特体育 · 商城</b>
          <i>运动装备一站式采购</i>
        </div>
      </div>

      <el-form :model="form" label-position="top" @submit.prevent>
        <el-form-item label="手机号">
          <el-input
            v-model="form.username"
            maxlength="11"
            placeholder="请输入11位手机号"
            clearable
            @keyup.enter="onLogin"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            @keyup.enter="onLogin"
          />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="login-btn" @click="onLogin">
          登录
        </el-button>
      </el-form>

      <div class="login-demo">
        <span class="login-demo__label">演示账号</span>
        <span>13800138000</span>
        <span class="login-demo__sep">/</span>
        <span>密码 123456</span>
      </div>

      <p class="login-tip">未登录时部分页面将自动跳转登录</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/store/modules/useAuthStore';

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();

/** 大陆手机号 */
const PHONE_REGEX = /^1[3-9]\d{9}$/;

const form = reactive({ username: '', password: '' });
const loading = ref(false);

async function onLogin(): Promise<void> {
  const phone = form.username.trim();
  if (!phone || !form.password) {
    ElMessage.warning('请输入手机号和密码');
    return;
  }
  if (!PHONE_REGEX.test(phone)) {
    ElMessage.warning('手机号格式不正确');
    return;
  }
  loading.value = true;
  try {
    await auth.login(phone, form.password);
    const redirect = (route.query.redirect as string) || '/home';
    router.replace(redirect);
  } catch (e) {
    ElMessage.error((e as Error).message || '登录失败');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 16px;
  background: radial-gradient(circle at 20% 20%, #e7f1ec 0%, #f5f5f5 45%, #f0f2f5 100%);
}
.login-card {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 14px;
  padding: 32px 34px 26px;
  box-shadow: 0 12px 40px rgba(18, 102, 79, 0.12);
}
.login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 26px;
}
.login-brand__mark {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--sent-primary), #1a8c6a);
  color: #fff;
  font-size: 26px;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(18, 102, 79, 0.28);
}
.login-brand__text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}
.login-brand__text b {
  font-size: 19px;
  color: var(--sent-primary);
  letter-spacing: 1px;
}
.login-brand__text i {
  font-style: normal;
  font-size: 12px;
  color: var(--sent-muted);
}
.login-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  letter-spacing: 4px;
}
.login-demo {
  margin-top: 18px;
  padding: 10px 14px;
  border-radius: 8px;
  background: var(--sent-accent-soft);
  color: #a1520f;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}
.login-demo__label {
  background: var(--sent-accent);
  color: #fff;
  font-size: 12px;
  padding: 1px 8px;
  border-radius: 4px;
}
.login-demo__sep {
  color: #d19a6a;
}
.login-tip {
  color: #b5b9c0;
  font-size: 12px;
  text-align: center;
  margin: 14px 0 0;
}
</style>
