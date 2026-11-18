<template>
  <div class="login-wrap">
    <div class="login-card">
      <router-link to="/enterprise/login" class="login-enterprise-link" data-testid="enterprise-login-link">
        企业登录 ›
      </router-link>

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
            data-testid="auth-phone"
            clearable
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            data-testid="auth-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item v-if="mode === 'register'" label="确认密码">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入密码"
            data-testid="auth-confirm-password"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button
          type="primary"
          :loading="loading"
          class="login-btn"
          :data-testid="mode === 'register' ? 'auth-register-submit' : 'auth-login-submit'"
          @click="submit"
        >
          {{ mode === 'register' ? '注册并登录' : '登录' }}
        </el-button>
      </el-form>

      <!-- 注册账户 / 忘记密码统一放在登录按钮下方 -->
      <div class="login-links">
        <template v-if="mode === 'login'">
          <button type="button" data-testid="login-mode-register" @click="switchMode('register')">免费注册</button>
          <span class="login-links__sep">|</span>
          <router-link to="/forgot-password" data-testid="forgot-password-link">忘记密码</router-link>
        </template>
        <button v-else type="button" data-testid="login-mode-login" @click="switchMode('login')">已有账号？返回登录</button>
      </div>

      <el-checkbox v-model="agreed" class="login-agreement">
        已阅读并同意
        <a
          class="login-agreement__link"
          data-testid="agreement-service"
          @click.stop.prevent="openAgreement('service')"
        >《森特商城用户服务协议》</a>
        和
        <a
          class="login-agreement__link"
          data-testid="agreement-privacy"
          @click.stop.prevent="openAgreement('privacy')"
        >《隐私权政策》</a>
      </el-checkbox>

      <AgreementDialog v-model="agreementVisible" :type="agreementType" />

      <div v-if="mode === 'login'" class="login-demo">
        <span class="login-demo__label">演示账号</span>
        <span>13800138000</span>
        <span class="login-demo__sep">/</span>
        <span>密码 123456</span>
      </div>

      <p class="login-tip">
        {{ mode === 'register' ? '注册即同意使用默认头像与昵称，客户号由系统自动分配' : '未登录时部分页面将自动跳转登录' }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/store/modules/useAuthStore';
import AgreementDialog from '@/components/AgreementDialog.vue';
import type { AgreementType } from '@/components/agreement';

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();

/** 大陆手机号 */
const PHONE_REGEX = /^1[3-9]\d{9}$/;

const form = reactive({ username: '', password: '', confirmPassword: '' });
const loading = ref(false);
/** 登录 / 注册共用一张卡片：切换时保留手机号，清空密码避免误用 */
const mode = ref<'login' | 'register'>('login');
/** 协议勾选项作为展示口径，暂不作为登录拦截条件（演示环境保留测试账号） */
const agreed = ref(true);

/** 协议全文阅读弹窗：两段书名号文字是真链接，点开即读（2026-11-18 用户需求） */
const agreementVisible = ref(false);
const agreementType = ref<AgreementType>('service');
function openAgreement(type: AgreementType): void {
  agreementType.value = type;
  agreementVisible.value = true;
}

function switchMode(next: 'login' | 'register'): void {
  mode.value = next;
  form.password = '';
  form.confirmPassword = '';
}

function submit(): void {
  if (mode.value === 'register') void onRegister();
  else void onLogin();
}

function validatePhone(): string | null {
  const phone = form.username.trim();
  if (!phone || !form.password) {
    ElMessage.warning(mode.value === 'register' ? '请填写手机号和密码' : '请输入手机号和密码');
    return null;
  }
  if (!PHONE_REGEX.test(phone)) {
    ElMessage.warning('手机号格式不正确');
    return null;
  }
  return phone;
}

function redirectAfterAuth(): void {
  const target = (route.query.redirect as string) || '/home';
  void router.replace(target);
}

async function onLogin(): Promise<void> {
  const phone = validatePhone();
  if (!phone) return;
  loading.value = true;
  try {
    await auth.login(phone, form.password);
    redirectAfterAuth();
  } catch (e) {
    ElMessage.error((e as Error).message || '登录失败');
  } finally {
    loading.value = false;
  }
}

/** 注册：两次密码一致校验在前端先拦一道，服务端仍会独立校验（同一口径） */
async function onRegister(): Promise<void> {
  const phone = validatePhone();
  if (!phone) return;
  if (form.password.length < 6 || form.password.length > 20) {
    ElMessage.warning('密码长度需为 6~20 位');
    return;
  }
  if (form.password !== form.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致');
    return;
  }
  loading.value = true;
  try {
    await auth.register({ phone, password: form.password, confirmPassword: form.confirmPassword });
    ElMessage.success('注册成功，已自动登录');
    redirectAfterAuth();
  } catch (e) {
    ElMessage.error((e as Error).message || '注册失败');
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
  position: relative;
  width: 100%;
  max-width: 400px;
  background: #fff;
  border-radius: 14px;
  padding: 32px 34px 26px;
  box-shadow: 0 12px 40px rgba(18, 102, 79, 0.12);
}
.login-enterprise-link {
  position: absolute;
  top: 18px;
  right: 22px;
  font-size: 13px;
  color: var(--sent-muted);
}
.login-enterprise-link:hover { color: var(--sent-primary); }
.login-links {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 14px;
  font-size: 13px;
}
.login-links button {
  border: none;
  background: transparent;
  font: inherit;
  color: var(--sent-primary);
  cursor: pointer;
  padding: 0;
}
.login-links a { color: var(--sent-muted); }
.login-links a:hover { color: var(--sent-primary); }
.login-links__sep { color: #d7d9dd; }
.login-agreement {
  margin-top: 14px !important;
  align-items: flex-start;
}
.login-agreement :deep(.el-checkbox__label) {
  font-size: 12px;
  line-height: 1.6;
  color: var(--sent-muted);
}
.login-agreement__link {
  color: var(--sent-primary);
  cursor: pointer;
  text-decoration: none;
}
.login-agreement__link:hover { text-decoration: underline; }
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
