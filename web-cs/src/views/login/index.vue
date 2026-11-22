<template>
  <div class="cs-login">
    <!-- 深色底 + 品牌绿光晕：客服每天要盯 8 小时，浅色大底太刺眼，这里只留一块卡片 -->
    <div class="cs-login__glow" aria-hidden="true" />

    <div class="cs-login__card">
      <div class="cs-login__brand">
        <span class="cs-login__logo">S</span>
        <div>
          <h1 class="cs-login__title">森特客服工作台</h1>
          <p class="cs-login__sub">仅限客服人员登录</p>
        </div>
      </div>

      <form class="cs-login__form" @submit.prevent="submit">
        <label class="cs-login__field">
          <span class="cs-login__label">用户名</span>
          <input
            v-model.trim="form.username"
            class="cs-login__input"
            type="text"
            autocomplete="username"
            placeholder="请输入工号 / 用户名"
            :disabled="loading"
          />
        </label>

        <label class="cs-login__field">
          <span class="cs-login__label">密码</span>
          <input
            v-model="form.password"
            class="cs-login__input"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            :disabled="loading"
          />
        </label>

        <!-- 权限不足 / 账号密码错误统一红字内联，不用 toast：错误要停在原地等用户看清 -->
        <p v-if="errorText" class="cs-login__error" role="alert">{{ errorText }}</p>

        <button class="cs-login__submit" type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登录' }}
        </button>
      </form>

      <p class="cs-login__foot">账号由管理员统一分配，忘记密码请联系系统管理员</p>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 客服登录页：深色极简、只有账号密码两个输入。
 *
 * 三条硬规则（对应任务第二步）：
 * 1. 只调公开端点 /auth/login，登录成功才拿 Token。
 * 2. 紧接着调 /admin/me 读 permissions，**必须**含 chat:serve 才算客服。
 * 3. 不是客服 → 清 Token、拒绝跳转、红字提示；绝不把半登录状态留在本地。
 */
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NotAgentError, useAuthStore } from '@/store/modules/useAuthStore';
import { usePresenceStore } from '@/store/modules/presence';
import { resolvePostLoginPath } from '@/utils/csAccess';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const presence = usePresenceStore();

const form = reactive({ username: '', password: '' });
const loading = ref(false);
const errorText = ref('');

/** 被守卫踢回来时（reason=forbidden），进页面就说清原因，不让用户反复试密码 */
onMounted(() => {
  if (route.query.reason === 'forbidden') {
    errorText.value = '权限不足，您不是客服人员';
  } else if (route.query.reason === 'expired') {
    errorText.value = '登录状态已失效，请重新登录';
  }
});

async function submit(): Promise<void> {
  if (loading.value) return;
  errorText.value = '';

  if (!form.username) {
    errorText.value = '请输入用户名';
    return;
  }
  if (!form.password) {
    errorText.value = '请输入密码';
    return;
  }

  loading.value = true;
  try {
    // 登录 + 拉权限 + 校验 chat:serve 是一个原子动作，任一不满足都会清 Token
    await auth.signIn(form.username, form.password);
    // 2026-11-21 用户要求：登录后默认离线，客服点「在线」才开始接单。
    // 这里必须落库（不只是改前端），否则后端仍会把新客户派给刚登录的人。
    await presence.reset();
    await router.replace(resolvePostLoginPath(String(route.query.redirect ?? '')));
  } catch (cause) {
    if (cause instanceof NotAgentError) {
      errorText.value = '权限不足，您不是客服人员';
    } else {
      errorText.value = (cause as Error)?.message || '登录失败，请稍后重试';
    }
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.cs-login {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background:
    radial-gradient(900px 520px at 50% -10%, rgba(18, 102, 79, 0.42), transparent 70%),
    linear-gradient(180deg, var(--cs-shell-2) 0%, #07110e 100%);
  overflow: hidden;
}

/* 一层极淡的品牌绿光晕，纯装饰 */
.cs-login__glow {
  position: absolute;
  width: 620px;
  height: 620px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 122, 26, 0.14), transparent 62%);
  filter: blur(8px);
  pointer-events: none;
}

.cs-login__card {
  position: relative;
  width: 360px;
  padding: 34px 32px 26px;
  border-radius: 16px;
  background: rgba(16, 35, 29, 0.88);
  border: 1px solid var(--cs-shell-line);
  box-shadow: var(--cs-shadow-pop);
  backdrop-filter: blur(10px);
}

.cs-login__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 26px;
}
.cs-login__logo {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 11px;
  background: linear-gradient(160deg, #1b8a6b, var(--sent-primary));
  color: #fff;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.cs-login__title {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--cs-shell-text);
  letter-spacing: 0.4px;
}
.cs-login__sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--cs-shell-text-dim);
}

.cs-login__form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.cs-login__field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.cs-login__label {
  font-size: 12px;
  color: var(--cs-shell-text-dim);
  letter-spacing: 0.3px;
}
.cs-login__input {
  height: 42px;
  padding: 0 12px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(255, 255, 255, 0.06);
  color: var(--cs-shell-text);
  font-size: 14px;
  outline: none;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.cs-login__input::placeholder {
  color: rgba(234, 243, 239, 0.35);
}
.cs-login__input:focus {
  border-color: rgba(27, 138, 107, 0.9);
  background: rgba(255, 255, 255, 0.09);
}
.cs-login__input:disabled {
  opacity: 0.6;
}

.cs-login__error {
  margin: 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: rgba(210, 44, 44, 0.14);
  border: 1px solid rgba(210, 44, 44, 0.42);
  color: #ff6b6b;
  font-size: 12.5px;
  line-height: 1.5;
}

.cs-login__submit {
  height: 42px;
  margin-top: 4px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(160deg, #1b8a6b, var(--sent-primary));
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  letter-spacing: 1px;
  cursor: pointer;
  transition: filter 0.15s ease, transform 0.05s ease;
}
.cs-login__submit:hover:not(:disabled) {
  filter: brightness(1.08);
}
.cs-login__submit:active:not(:disabled) {
  transform: translateY(1px);
}
.cs-login__submit:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}

.cs-login__foot {
  margin: 20px 0 0;
  font-size: 11.5px;
  color: rgba(157, 179, 170, 0.72);
  text-align: center;
  line-height: 1.6;
}
</style>
