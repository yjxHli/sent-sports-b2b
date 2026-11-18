<template>
  <div class="ent-auth-wrap">
    <div class="ent-auth-card">
      <router-link to="/login" class="ent-auth-back" data-testid="personal-login-link">个人登录 ›</router-link>
      <h1>企业账号登录</h1>
      <p class="ent-auth-sub">森特体育企业采购专属入口 · 审核通过后方可登录</p>

      <div class="ent-tabs">
        <button type="button" class="ent-tabs__tab ent-tabs__tab--on">密码登录</button>
        <button type="button" class="ent-tabs__tab" disabled data-testid="enterprise-sms-tab">
          短信登录（暂未开放）
        </button>
      </div>

      <el-form :model="form" label-position="top" @submit.prevent>
        <el-form-item label="登录账号 / 手机号">
          <el-input
            v-model="form.username"
            placeholder="请输入注册手机号"
            data-testid="enterprise-account"
            clearable
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入登录密码"
            data-testid="enterprise-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <div class="ent-forgot">
          <router-link to="/forgot-password" data-testid="enterprise-forgot-link">忘记密码？</router-link>
        </div>
        <el-button type="primary" class="ent-submit" :loading="loading"
                   data-testid="enterprise-login-submit" @click="submit">登 录</el-button>
      </el-form>

      <p class="ent-register-tip">
        还没有账号？
        <router-link to="/enterprise/register" data-testid="enterprise-register-link">注册账号</router-link>
      </p>

      <div class="ent-demo">
        <span>企业演示账号</span>
        <b>13900009101</b><span>/ 123456</span>
        <small>（演示企业已审核通过）</small>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/store/modules/useAuthStore';

const auth = useAuthStore();
const router = useRouter();
const form = reactive({ username: '', password: '' });
const loading = ref(false);

async function submit(): Promise<void> {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入账号和密码');
    return;
  }
  loading.value = true;
  try {
    await auth.login(form.username.trim(), form.password);
    ElMessage.success('企业账号登录成功');
    await router.replace('/home');
  } catch (e) {
    ElMessage.error((e as Error).message || '登录失败，请检查账号或等待审核');
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.ent-auth-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 16px;
  background: radial-gradient(circle at 20% 20%, #e7f1ec 0%, #f5f5f5 50%, #f0f2f5 100%);
}
.ent-auth-card {
  position: relative;
  width: 100%;
  max-width: 420px;
  padding: 38px 36px 28px;
  background: #fff;
  border-radius: 14px;
  box-shadow: 0 12px 40px rgba(18, 102, 79, 0.12);
}
.ent-auth-back { position: absolute; top: 18px; right: 22px; font-size: 13px; color: var(--sent-muted); }
.ent-auth-back:hover { color: var(--sent-primary); }
.ent-auth-card h1 { margin: 0; font-size: 26px; text-align: center; color: #1f2d28; }
.ent-auth-sub { margin: 10px 0 24px; text-align: center; font-size: 13px; color: var(--sent-muted); }
.ent-tabs { display: flex; gap: 24px; justify-content: center; margin-bottom: 22px; }
.ent-tabs__tab {
  border: none; background: transparent; padding: 6px 2px 10px;
  font: inherit; font-size: 16px; color: #8a94a6; cursor: pointer;
}
.ent-tabs__tab--on { color: var(--sent-primary); font-weight: 700; border-bottom: 2px solid var(--sent-primary); }
.ent-tabs__tab:disabled { cursor: not-allowed; color: #c3c9d1; }
.ent-forgot { text-align: right; margin: -4px 0 12px; font-size: 13px; }
.ent-forgot a { color: #d9822b; }
.ent-submit { width: 100%; height: 44px; font-size: 16px; letter-spacing: 4px; }
.ent-register-tip { margin: 16px 0 0; text-align: center; font-size: 13px; color: var(--sent-muted); }
.ent-register-tip a, .ent-demo a { color: var(--sent-primary); }
.ent-demo {
  margin-top: 18px; padding: 10px 12px; border-radius: 8px;
  background: var(--sent-accent-soft); color: #a1520f;
  font-size: 12px; display: flex; flex-wrap: wrap; gap: 6px; justify-content: center;
}
.ent-demo small { width: 100%; text-align: center; color: #b88655; }
</style>
