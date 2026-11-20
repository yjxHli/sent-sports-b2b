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
            <el-input
              v-model="phone"
              maxlength="11"
              clearable
              placeholder="请输入11位手机号"
              data-testid="forgot-phone"
            />
          </el-form-item>
          <el-form-item label="验证码">
            <div class="forgot-code-row">
              <el-input
                v-model="code"
                maxlength="6"
                placeholder="请输入验证码"
                data-testid="forgot-code"
                @keyup.enter="nextStep"
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
        <p class="forgot-demo">演示环境：验证码将打印到浏览器控制台（60 秒内有效）</p>
        <el-button
          type="primary"
          class="forgot-btn"
          :loading="verifying"
          data-testid="forgot-next"
          @click="nextStep"
        >下一步</el-button>
      </template>

      <!-- Step 2：验证码 + 新密码 -->
      <template v-else-if="step === 2">
        <h1>设置新密码</h1>
        <p class="forgot-hint">手机号 {{ maskedPhone }} 已通过验证，请设置 6~20 位新密码。</p>
        <el-form label-position="top" @submit.prevent>
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
 * 找回密码：验证码与登录页走同一套后端机制——后端生成、进程内存储、打印控制台，
 * 开发环境把 demoCode 回显到浏览器控制台，生产默认关闭；验证码一次性消费。
 */
import { computed, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { authApi } from '@/api/auth';

const router = useRouter();

const PHONE_REGEX = /^1[3-9]\d{9}$/;

const step = ref(1);
const phone = ref('');
const code = ref('');
const password = ref('');
const confirmPassword = ref('');
const sending = ref(false);
const verifying = ref(false);
const submitting = ref(false);
const countdown = ref(0);
let countdownTimer: ReturnType<typeof setInterval> | undefined;

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

/** 获取验证码：后端存内存并打印控制台，前端只负责倒计时与防连点 */
async function sendCode(): Promise<void> {
  const phoneNo = phone.value.trim();
  if (!PHONE_REGEX.test(phoneNo)) {
    ElMessage.warning('请输入正确的手机号');
    return;
  }
  if (countdown.value > 0 || sending.value) return;
  sending.value = true;
  try {
    const res = await authApi.sendResetCode({ phone: phoneNo });
    // 与登录页一致：仅开发环境打印；后端 prod 默认不回显 demoCode，双重保护。
    if (import.meta.env.DEV && res.data?.demoCode) {
      console.info(`[森特演示] 找回密码验证码：${phoneNo} 的验证码 ${res.data.demoCode}（60 秒内有效）`);
    }
    ElMessage.success('验证码已发送，可在浏览器控制台查看');
    startCountdown();
  } catch (e) {
    ElMessage.error((e as Error).message || '验证码发送失败');
  } finally {
    sending.value = false;
  }
}

/** 第一步：校验手机号 + 验证码（后端只校验不消费），通过后才进入设置新密码 */
async function nextStep(): Promise<void> {
  const phoneNo = phone.value.trim();
  const codeNo = code.value.trim();
  if (!PHONE_REGEX.test(phoneNo)) {
    ElMessage.warning('请输入正确的手机号');
    return;
  }
  if (!/^\d{6}$/.test(codeNo)) {
    ElMessage.warning('请输入 6 位验证码');
    return;
  }
  if (verifying.value) return;
  verifying.value = true;
  try {
    await authApi.verifyResetCode({ phone: phoneNo, code: codeNo });
    step.value = 2;
  } catch (e) {
    ElMessage.error((e as Error).message || '验证码校验失败，请重新获取');
  } finally {
    verifying.value = false;
  }
}

/** 确认重置：后端校验并消费验证码，成功后新密码即时生效 */
async function submit(): Promise<void> {
  if (!/^\d{6}$/.test(code.value.trim())) {
    ElMessage.error('请输入 6 位验证码');
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
  try {
    await authApi.resetPassword({
      phone: phone.value.trim(),
      code: code.value.trim(),
      password: password.value,
      confirmPassword: confirmPassword.value,
    });
    step.value = 3;
    ElMessage.success('密码重置成功，请使用新密码登录');
  } catch (e) {
    ElMessage.error((e as Error).message || '重置失败，请稍后再试');
  } finally {
    submitting.value = false;
  }
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer);
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
