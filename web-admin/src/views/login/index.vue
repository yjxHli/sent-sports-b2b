<template>
  <div class="login-page">
    <div class="login-card">
      <h1>森特体育用品管理系统</h1>
      <p class="login-subtitle">管理端 · 请使用员工账号登录</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" size="large" placeholder="用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            size="large"
            placeholder="密码"
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="login-submit" :loading="loading" @click="submit">
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <p class="login-tip" :class="{ 'login-tip-locked': remaining > 0 }">
        {{ remaining > 0
          ? `账号已临时锁定，请 ${remaining} 秒后重试`
          : '连续登录失败将触发账号锁定，请联系系统管理员重置。' }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { useAuthStore } from '@/store/modules/useAuthStore';
import { usePermissionStore } from '@/store/modules/permission';

const router = useRouter();
const auth = useAuthStore();
const permission = usePermissionStore();

const formRef = ref<FormInstance>();
const loading = ref(false);
const form = reactive({ username: '', password: '' });
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

// 前端失败锁定：连续 5 次失败锁 60 秒，避免暴力试密码（后端仍应有自己的锁定策略）
const MAX_FAILS = 5;
const LOCK_SECONDS = 60;
const failCount = ref(0);
const lockedUntil = ref(0);
const remaining = ref(0);
let timer: ReturnType<typeof setInterval> | undefined;

function startCountdown(): void {
  if (timer) return;
  timer = setInterval(() => {
    remaining.value = Math.max(0, Math.ceil((lockedUntil.value - Date.now()) / 1000));
    if (remaining.value === 0) {
      clearInterval(timer);
      timer = undefined;
      failCount.value = 0;
    }
  }, 500);
}
onUnmounted(() => { if (timer) clearInterval(timer); });

function registerFailure(): void {
  failCount.value += 1;
  if (failCount.value >= MAX_FAILS) {
    lockedUntil.value = Date.now() + LOCK_SECONDS * 1000;
    remaining.value = LOCK_SECONDS;
    startCountdown();
  }
}

async function submit(): Promise<void> {
  if (!formRef.value || loading.value) return;
  if (remaining.value > 0) {
    ElMessage.warning(`登录失败次数过多，请 ${remaining.value} 秒后重试`);
    return;
  }
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) return;

  loading.value = true;
  try {
    // store.login 内部完成 TokenVO -> setToken（双令牌 + expiresIn 转时间戳）
    await auth.login(form.username, form.password);
    failCount.value = 0;
    remaining.value = 0;
    permission.reset();
    await permission.load(true);
    /**
     * 管理端统一落地页是工作台。
     * 不再使用 query.redirect：401 回跳地址由 HTTP 内核用 window.location.pathname 生成，
     * 它带 /admin 基路径，交给 router.replace 会再拼一次 base 变成 /admin/admin/xxx → 404。
     */
    await router.replace('/dashboard');
  } catch (error) {
    registerFailure();
    const hint = remaining.value > 0
      ? `连续失败 ${MAX_FAILS} 次，已锁定 ${remaining.value} 秒`
      : `还可尝试 ${Math.max(0, MAX_FAILS - failCount.value)} 次`;
    ElMessage.error(`${(error as Error)?.message || '登录失败，请检查账号密码'}（${hint}）`);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f4738 0%, #12664f 55%, #1c8a68 100%);
}
.login-card {
  width: 400px;
  padding: 36px 36px 28px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 18px 48px rgba(0, 0, 0, 0.18);
}
.login-card h1 {
  margin: 0;
  font-size: 21px;
  text-align: center;
  color: #12664f;
}
.login-subtitle {
  margin: 8px 0 28px;
  text-align: center;
  font-size: 13px;
  color: #7b8a84;
}
.login-submit { width: 100%; }
.login-tip {
  margin: 4px 0 0;
  font-size: 12px;
  color: #9aa8a2;
  text-align: center;
}
.login-tip-locked { color: #d03050; font-weight: 600; }
</style>
