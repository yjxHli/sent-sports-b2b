<template>
  <div class="ent-auth-wrap">
    <div class="ent-auth-card">
      <router-link to="/login" class="ent-auth-back" data-testid="personal-login-link">个人登录 ›</router-link>
      <h1>企业账号登录</h1>
      <p class="ent-auth-sub">森特体育企业采购专属入口 · 审核通过后方可登录</p>

      <div class="ent-tabs">
        <button type="button" class="ent-tabs__tab" :class="{ 'ent-tabs__tab--on': loginType === 'password' }"
                data-testid="enterprise-password-tab" @click="loginType = 'password'">密码登录</button>
        <button type="button" class="ent-tabs__tab" :class="{ 'ent-tabs__tab--on': loginType === 'code' }"
                data-testid="enterprise-sms-tab" @click="loginType = 'code'">短信登录</button>
      </div>

      <el-form :model="form" label-position="top" @submit.prevent>
        <el-form-item label="登录账号 / 手机号">
          <el-input
            v-model="form.username"
            placeholder="请输入注册手机号"
            data-testid="enterprise-account"
            maxlength="11"
            clearable
            @keyup.enter="submit"
          />
        </el-form-item>
        <template v-if="loginType === 'password'">
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
        </template>
        <template v-else>
          <el-form-item label="短信验证码">
            <div class="code-row">
              <el-input v-model="form.code" maxlength="6" placeholder="请输入 6 位验证码"
                        data-testid="enterprise-code" @keyup.enter="submit" />
              <el-button data-testid="enterprise-send-code" :disabled="countdown > 0" @click="sendCode">
                {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>
        </template>
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

      <router-link to="/enterprise/audit-progress" class="ent-progress-link" data-testid="enterprise-audit-progress-link">
        查看审核进度 ›
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { authApi } from '@/api/auth';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { unwrap } from '@/utils/http';

const auth = useAuthStore();
const router = useRouter();
const form = reactive({ username: '', password: '', code: '' });
const loginType = ref<'password' | 'code'>('password');
const loading = ref(false);
const countdown = ref(0);
let countdownTimer: ReturnType<typeof setInterval> | null = null;

const PHONE = /^1[3-9]\d{9}$/;

function startCountdown(): void {
  countdown.value = 60;
  countdownTimer = setInterval(() => {
    countdown.value = Math.max(0, countdown.value - 1);
    if (countdown.value === 0 && countdownTimer) {
      clearInterval(countdownTimer);
      countdownTimer = null;
    }
  }, 1000);
}

async function sendCode(): Promise<void> {
  const phone = form.username.trim();
  if (!PHONE.test(phone)) {
    ElMessage.warning('请输入正确的注册手机号');
    return;
  }
  try {
    const res = await unwrap(authApi.sendCode({ phone }));
    if (import.meta.env.DEV && res?.demoCode) {
      console.info(`[森特演示] 企业登录验证码：${res.demoCode}`);
    }
    ElMessage.success('验证码已发送，可在浏览器控制台查看');
    startCountdown();
  } catch (e) {
    ElMessage.error((e as Error).message || '验证码发送失败');
  }
}

async function submit(): Promise<void> {
  const phone = form.username.trim();
  if (!PHONE.test(phone)) {
    ElMessage.warning('请输入正确的注册手机号');
    return;
  }
  loading.value = true;
  try {
    if (loginType.value === 'code') {
      if (!/^\d{6}$/.test(form.code.trim())) {
        ElMessage.warning('请输入 6 位短信验证码');
        return;
      }
      await auth.loginByCode(phone, form.code.trim());
    } else {
      if (!form.password) {
        ElMessage.warning('请输入登录密码');
        return;
      }
      await auth.login(phone, form.password);
    }
    ElMessage.success('企业账号登录成功');
    await router.replace('/home');
  } catch (e) {
    const err = e as Error & { code?: number };
    if (err.code === 42201) {
      ElMessage.error('企业资料尚未审核通过，请先查看审核进度');
    } else {
      ElMessage.error(err.message || '登录失败，请检查账号或等待审核');
    }
  } finally {
    loading.value = false;
  }
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer);
});
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
.ent-forgot { text-align: right; margin: -4px 0 12px; font-size: 13px; }
.ent-forgot a { color: #d9822b; }
.code-row { display: flex; gap: 10px; width: 100%; }
.code-row .el-button { flex: none; }
.ent-submit { width: 100%; height: 44px; font-size: 16px; letter-spacing: 4px; }
.ent-register-tip { margin: 16px 0 0; text-align: center; font-size: 13px; color: var(--sent-muted); }
.ent-register-tip a, .ent-demo a { color: var(--sent-primary); }
.ent-demo {
  margin-top: 18px; padding: 10px 12px; border-radius: 8px;
  background: var(--sent-accent-soft); color: #a1520f;
  font-size: 12px; display: flex; flex-wrap: wrap; gap: 6px; justify-content: center;
}
.ent-demo small { width: 100%; text-align: center; color: #b88655; }
.ent-progress-link {
  position: absolute; right: 20px; bottom: 12px;
  font-size: 12px; color: var(--sent-primary); text-decoration: none;
}
.ent-progress-link:hover { text-decoration: underline; }
</style>
