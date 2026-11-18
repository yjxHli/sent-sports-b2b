<template>
  <div class="forgot-wrap">
    <div class="forgot-card">
      <router-link to="/login" class="forgot-back" data-testid="forgot-back-login">返回登录 ›</router-link>

      <div class="forgot-brand">
        <span class="forgot-brand__mark">S</span>
        <div class="forgot-brand__text">
          <b>森特体育 · 商城</b>
          <i>找回密码</i>
        </div>
      </div>

      <el-steps :active="step - 1" align-center finish-status="success" class="forgot-steps">
        <el-step title="验证手机号" />
        <el-step title="设置新密码" />
        <el-step title="完成" />
      </el-steps>

      <!-- Step 1：手机号 + 短信验证码 -->
      <template v-if="step === 1">
        <h1>找回密码</h1>
        <p class="forgot-hint">请输入注册手机号，获取短信验证码后即可重置登录密码。</p>
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="手机号">
            <div class="forgot-code-row">
              <el-input
                v-model="phone"
                maxlength="11"
                clearable
                placeholder="请输入11位手机号"
                data-testid="forgot-phone"
                @keyup.enter="sendCode"
              />
              <el-button
                class="forgot-code-btn"
                :disabled="countdown > 0"
                :loading="sending"
                data-testid="forgot-send-code"
                @click="sendCode"
              >{{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}</el-button>
            </div>
          </el-form-item>
        </el-form>
        <p class="forgot-demo">演示环境验证码：<b>123456</b></p>
        <el-button
          type="primary"
          class="forgot-btn"
          data-testid="forgot-next"
          @click="nextStep"
        >下一步</el-button>
      </template>

      <!-- Step 2：验证码 + 新密码 -->
      <template v-else-if="step === 2">
        <h1>设置新密码</h1>
        <p class="forgot-hint">手机号 {{ maskedPhone }} 已通过验证，请设置 6~20 位新密码。</p>
        <el-form label-position="top" @submit.prevent>
          <el-form-item label="短信验证码">
            <el-input
              v-model="code"
              maxlength="6"
              placeholder="请输入6位验证码"
              data-testid="forgot-code"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-form-item label="新密码">
            <el-input
              v-model="password"
              type="password"
              show-password
              placeholder="6~20 位新密码"
              data-testid="forgot-new-password"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input
              v-model="confirmPassword"
              type="password"
              show-password
              placeholder="请再次输入新密码"
              data-testid="forgot-confirm-password"
              @keyup.enter="submit"
            />
          </el-form-item>
        </el-form>
        <div class="forgot-actions">
          <el-button data-testid="forgot-prev" @click="step = 1">上一步</el-button>
          <el-button
            type="primary"
            :loading="submitting"
            data-testid="forgot-submit"
            @click="submit"
          >确认重置</el-button>
        </div>
      </template>

      <!-- Step 3：完成 -->
      <template v-else>
        <div class="forgot-done">
          <div class="forgot-done__icon">✓</div>
          <h1>密码重置成功</h1>
          <p class="forgot-hint">请使用新密码登录。为保护账号安全，请勿向他人透露密码。</p>
          <el-button
            type="primary"
            class="forgot-btn"
            data-testid="forgot-done-login"
            @click="router.replace('/login')"
          >返回登录</el-button>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 找回密码（2026-11-18 用户要求：先把前端页面搭起来）。
 *
 * 当前后端 /api/auth 尚未提供「发送重置验证码 / 重置密码」端点，
 * 这里用演示流程把三步表单、校验、倒计时、成功页完整落地，验证码固定 123456。
 * 后端接口就绪后，只需把 sendCode() 与 submit() 中的演示分支替换为
 * authApi.sendResetCode({ phone }) / authApi.resetPassword({ phone, code, password })，
 * UI 与 data-testid 契约保持不变。
 */
import { computed, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

const router = useRouter();

const PHONE_REGEX = /^1[3-9]\d{9}$/;
const DEMO_CODE = '123456';

const step = ref(1);
const phone = ref('');
const code = ref('');
const password = ref('');
const confirmPassword = ref('');
const sending = ref(false);
const submitting = ref(false);
const countdown = ref(0);
let countdownTimer: ReturnType<typeof setInterval> | undefined;
/** 演示态的异步占位，让按钮 loading 与真实接口体验一致 */
let demoTimer: ReturnType<typeof setTimeout> | undefined;

const maskedPhone = computed(() => {
  const value = phone.value.trim();
  return value.length === 11 ? `${value.slice(0, 3)}****${value.slice(7)}` : value;
});

function startCountdown(): void {
  countdown.value = 60;
  countdownTimer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0 && countdownTimer) {
      clearInterval(countdownTimer);
      countdownTimer = undefined;
    }
  }, 1000);
}

/** TODO(后端就绪)：替换为 authApi.sendResetCode({ phone }) */
function sendCode(): void {
  if (!PHONE_REGEX.test(phone.value.trim())) {
    ElMessage.warning('请输入正确的手机号');
    return;
  }
  if (countdown.value > 0 || sending.value) return;
  sending.value = true;
  demoTimer = setTimeout(() => {
    sending.value = false;
    startCountdown();
    ElMessage.success(`验证码已发送，演示验证码 ${DEMO_CODE}`);
  }, 400);
}

function nextStep(): void {
  if (!PHONE_REGEX.test(phone.value.trim())) {
    ElMessage.warning('请输入正确的手机号');
    return;
  }
  if (!code.value.trim()) {
    ElMessage.warning('请先获取并填写验证码');
    return;
  }
  if (code.value.trim() !== DEMO_CODE) {
    ElMessage.error('验证码不正确，请重新输入');
    return;
  }
  step.value = 2;
}

/** TODO(后端就绪)：替换为 authApi.resetPassword({ phone, code, password }) */
function submit(): void {
  if (code.value.trim() !== DEMO_CODE) {
    ElMessage.error('验证码不正确，请返回上一步重新获取');
    return;
  }
  if (password.value.length < 6 || password.value.length > 20) {
    ElMessage.warning('密码长度需为 6~20 位');
    return;
  }
  if (password.value !== confirmPassword.value) {
    ElMessage.warning('两次输入的密码不一致');
    return;
  }
  submitting.value = true;
  demoTimer = setTimeout(() => {
    submitting.value = false;
    step.value = 3;
    ElMessage.success('演示环境：密码校验通过，尚未实际修改服务端密码');
  }, 500);
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer);
  if (demoTimer) clearTimeout(demoTimer);
});
</script>

<style scoped>
.forgot-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 16px;
  background: radial-gradient(circle at 20% 20%, #e7f1ec 0%, #f5f5f5 45%, #f0f2f5 100%);
}
.forgot-card {
  position: relative;
  width: 100%;
  max-width: 440px;
  background: #fff;
  border-radius: 14px;
  padding: 32px 34px 28px;
  box-shadow: 0 12px 40px rgba(18, 102, 79, 0.12);
}
.forgot-back {
  position: absolute;
  top: 18px;
  right: 22px;
  font-size: 13px;
  color: var(--sent-muted);
}
.forgot-back:hover { color: var(--sent-primary); }
.forgot-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}
.forgot-brand__mark {
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
.forgot-brand__text { display: flex; flex-direction: column; line-height: 1.25; }
.forgot-brand__text b { font-size: 18px; color: var(--sent-primary); letter-spacing: 0.5px; }
.forgot-brand__text i { font-style: normal; font-size: 12px; color: var(--sent-muted); }
.forgot-steps { margin-bottom: 22px; }
.forgot-card h1 { margin: 0 0 8px; font-size: 20px; color: var(--sent-text); }
.forgot-hint { margin: 0 0 18px; font-size: 12px; line-height: 1.7; color: var(--sent-muted); }
.forgot-demo {
  margin: -4px 0 16px;
  font-size: 12px;
  color: #a1520f;
  background: var(--sent-accent-soft);
  border-radius: 6px;
  padding: 8px 10px;
}
.forgot-demo b { font-size: 14px; }
.forgot-code-row { display: flex; gap: 8px; width: 100%; }
.forgot-code-btn { flex: none; white-space: nowrap; }
.forgot-btn { width: 100%; height: 44px; font-size: 16px; letter-spacing: 4px; }
.forgot-actions { display: flex; gap: 10px; }
.forgot-actions .forgot-btn, .forgot-actions .el-button:last-child { flex: 1; }
.forgot-done { text-align: center; }
.forgot-done__icon {
  width: 58px;
  height: 58px;
  margin: 6px auto 16px;
  border-radius: 50%;
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
  font-size: 30px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
