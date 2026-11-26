<template>
  <div class="login-wrap">
    <div class="login-card">
      <router-link to="/enterprise/login" class="login-enterprise-link" data-testid="enterprise-login-link">
        企业登录 ›
      </router-link>

      <div class="login-brand">
        <img class="login-brand__mark" src="/sent-icon-192.png" alt="" />
        <div class="login-brand__text">
          <b>森特体育 · 商城</b>
          <i>运动装备一站式采购</i>
        </div>
      </div>

      <!-- 登录模式内：密码登录 / 验证码登录 -->
      <div v-if="mode === 'login'" class="login-tabs">
        <button
          type="button"
          aria-label="密码"
          :class="['login-tabs__item', { 'is-active': loginType === 'password' }]"
          data-testid="login-tab-password"
          @click="switchLoginType('password')"
        >密码登录</button>
        <button
          type="button"
          aria-label="验证码"
          :class="['login-tabs__item', { 'is-active': loginType === 'code' }]"
          data-testid="login-tab-code"
          @click="switchLoginType('code')"
        >验证码登录</button>
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

        <el-form-item v-if="mode === 'register' || loginType === 'password'" label="密码">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            data-testid="auth-password"
            @keyup.enter="submit"
          />
        </el-form-item>

        <el-form-item v-if="mode === 'login' && loginType === 'code'" label="验证码">
          <div class="login-code-row">
            <el-input
              v-model="form.code"
              maxlength="6"
              placeholder="请输入验证码"
              data-testid="auth-code"
              @keyup.enter="submit"
            />
            <el-button
              class="login-code-btn"
              :disabled="countdown > 0"
              :loading="sending"
              data-testid="auth-send-code"
              @click="sendCode"
            >{{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}</el-button>
          </div>
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

      <div v-if="mode === 'login' && loginType === 'password'" class="login-demo">
        <span class="login-demo__label">演示账号</span>
        <span>13800138000</span>
        <span class="login-demo__sep">/</span>
        <span>密码 123456</span>
      </div>

      <p class="login-tip">
        {{ mode === 'register'
          ? '注册即同意使用默认头像与昵称，客户号由系统自动分配'
          : (loginType === 'code' ? '验证码 60 秒内有效，开发环境可在浏览器控制台查看' : '未登录时部分页面将自动跳转登录') }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onUnmounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ErrorCode } from '@sent/shared';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { authApi } from '@/api/auth';
import AgreementDialog from '@/components/AgreementDialog.vue';
import type { AgreementType } from '@/components/agreement';

const auth = useAuthStore();
const router = useRouter();
const route = useRoute();

/** 大陆手机号 */
const PHONE_REGEX = /^1[3-9]\d{9}$/;
/** 未注册手机号被拦截后的落地页（复用本组件，query.phone 回填） */
const REGISTER_PATH = '/register';

const form = reactive({ username: '', password: '', confirmPassword: '', code: '' });
const loading = ref(false);
/** 登录 / 注册共用一张卡片：切换时保留手机号，清空密码避免误用 */
const mode = ref<'login' | 'register'>('login');
/** 登录模式内的两种凭证方式 */
const loginType = ref<'password' | 'code'>('password');
/** 协议勾选项作为展示口径，暂不作为登录拦截条件（演示环境保留测试账号） */
const agreed = ref(true);

/** 验证码倒计时：与后端 60s 有效期一致，按钮置灰防连点 */
const countdown = ref(0);
const sending = ref(false);
let countdownTimer: ReturnType<typeof setInterval> | undefined;

/**
 * /register 与 /login 复用同一组件：进入注册路由时切到注册模式，
 * 并把 query.phone 回填到手机号输入框，用户无需重复输入。
 */
watch(() => route.fullPath, () => {
  if (route.path === REGISTER_PATH) {
    mode.value = 'register';
    const phone = route.query.phone;
    if (typeof phone === 'string' && phone) form.username = phone;
    form.password = '';
    form.confirmPassword = '';
    form.code = '';
  } else if (route.path === '/login') {
    mode.value = 'login';
  }
}, { immediate: true });

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer);
});

/** 协议全文阅读弹窗 */
const agreementVisible = ref(false);
const agreementType = ref<AgreementType>('service');
function openAgreement(type: AgreementType): void {
  agreementType.value = type;
  agreementVisible.value = true;
}

function switchLoginType(next: 'password' | 'code'): void {
  loginType.value = next;
  form.code = '';
}

function switchMode(next: 'login' | 'register'): void {
  // 从独立注册路由返回登录：走路由切换，watch 负责把 mode 切回来
  if (next === 'login' && route.path === REGISTER_PATH) {
    void router.replace('/login');
    return;
  }
  mode.value = next;
  form.password = '';
  form.confirmPassword = '';
  form.code = '';
}

function submit(): void {
  if (mode.value === 'register') void onRegister();
  else void onLogin();
}

function validatePhone(): string | null {
  const phone = form.username.trim();
  if (!phone) {
    ElMessage.warning('请输入手机号');
    return null;
  }
  if (!PHONE_REGEX.test(phone)) {
    ElMessage.warning('手机号格式不正确');
    return null;
  }
  if (mode.value === 'register' || loginType.value === 'password') {
    if (!form.password) {
      ElMessage.warning('请输入密码');
      return null;
    }
  } else if (!form.code.trim()) {
    ElMessage.warning('请输入验证码');
    return null;
  }
  return phone;
}

function redirectAfterAuth(): void {
  const target = (route.query.redirect as string) || '/home';
  void router.replace(target);
}

function startCountdown(): void {
  countdown.value = 60;
  if (countdownTimer) clearInterval(countdownTimer);
  countdownTimer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer);
      countdownTimer = undefined;
    }
  }, 1000);
}

/** 获取验证码：后端存内存并打印控制台，前端只负责倒计时与防连点 */
async function sendCode(): Promise<void> {
  const phone = form.username.trim();
  if (!PHONE_REGEX.test(phone)) {
    ElMessage.warning('请输入正确的手机号');
    return;
  }
  if (countdown.value > 0 || sending.value) return;
  sending.value = true;
  try {
    const res = await authApi.sendCode({ phone });
    // 演示：仅开发环境把验证码打印到浏览器控制台，方便查看（生产构建下 DEV=false 不打印，
    // 且后端 prod 默认不回显 demoCode，双重保护）。
    if (import.meta.env.DEV && res.data?.demoCode) {
      console.info(`[森特演示] 手机号 ${phone} 的验证码：${res.data.demoCode}（60 秒内有效）`);
    }
    ElMessage.success('验证码已发送，可在浏览器控制台查看');
    startCountdown();
  } catch (e) {
    ElMessage.error((e as Error).message || '验证码发送失败');
  } finally {
    sending.value = false;
  }
}

/** 未注册手机号：提示 + 跳注册页 + query 回填手机号（watch 负责写入输入框） */
function handleLoginError(error: unknown, phone: string): void {
  const err = error as { code?: number; message?: string };
  if (err?.code === ErrorCode.PHONE_NOT_REGISTERED) {
    ElMessage.warning('该手机号尚未注册，请先注册');
    const query: Record<string, string> = { phone };
    const redirect = route.query.redirect;
    if (typeof redirect === 'string' && redirect) query.redirect = redirect;
    void router.push({ path: REGISTER_PATH, query });
    return;
  }
  ElMessage.error(err?.message || '登录失败');
}

async function onLogin(): Promise<void> {
  const phone = validatePhone();
  if (!phone) return;
  loading.value = true;
  try {
    if (loginType.value === 'code') await auth.loginByCode(phone, form.code.trim());
    else await auth.login(phone, form.password);
    redirectAfterAuth();
  } catch (e) {
    handleLoginError(e, phone);
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
.login-tabs {
  display: flex;
  gap: 28px;
  margin-bottom: 20px;
  border-bottom: 1px solid #eef0f2;
}
.login-tabs__item {
  position: relative;
  border: none;
  background: transparent;
  padding: 0 0 10px;
  font: inherit;
  font-size: 15px;
  color: var(--sent-muted);
  cursor: pointer;
}
.login-tabs__item.is-active {
  color: var(--sent-primary);
  font-weight: 600;
}
.login-tabs__item.is-active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  border-radius: 2px;
  background: var(--sent-primary);
}
.login-code-row {
  display: flex;
  gap: 8px;
}
.login-code-row .el-input { flex: 1; min-width: 0; }
.login-code-btn {
  width: 118px;
  flex: none;
  padding: 0 8px;
  font-size: 13px;
}
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
  display: block;
  object-fit: contain;
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
