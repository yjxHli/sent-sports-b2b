<template>
  <div class="audit-wrap">
    <div class="audit-card">
      <router-link to="/enterprise/login" class="audit-back" data-testid="back-to-enterprise-login">返回企业登录 ›</router-link>
      <h1>企业审核进度</h1>
      <p class="audit-sub">输入注册手机号与短信验证码，查询企业资料审核状态</p>

      <div class="audit-form">
        <el-input v-model="phone" maxlength="11" placeholder="请输入注册手机号" data-testid="audit-phone" />
        <div class="audit-code-row">
          <el-input v-model="code" maxlength="6" placeholder="请输入 6 位短信验证码" data-testid="audit-code" />
          <el-button data-testid="audit-send-code" :disabled="countdown > 0" @click="sendCode">
            {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
          </el-button>
        </div>
        <div v-if="error" class="audit-error" data-testid="audit-error">{{ error }}</div>
        <el-button type="primary" class="audit-submit" :loading="loading"
                   data-testid="audit-query" @click="query">查询进度</el-button>
      </div>

      <div v-if="progress" class="audit-result" data-testid="audit-result">
        <div class="audit-status" :class="statusClass" data-testid="audit-status">
          {{ statusLabel }}
        </div>

        <template v-if="status === 'PENDING'">
          <p class="audit-hint" data-testid="audit-pending-tip">您的企业资料正在审核中，请耐心等待，审核结果将通过本页展示。</p>
        </template>
        <template v-else-if="status === 'REJECTED'">
          <div class="audit-reject" data-testid="audit-reject-reason">
            <b>审核未通过，原因如下：</b>
            <p>{{ progress.auditRemark || '资料不符合要求，请重新提交' }}</p>
          </div>
          <p class="audit-hint">请按上述原因修改后重新提交资料。</p>
        </template>
        <template v-else>
          <p class="audit-pass-tip" data-testid="audit-pass-tip">审核通过，可以使用企业账号登录了。</p>
          <el-button type="primary" @click="router.push('/enterprise/login')">前往企业登录</el-button>
        </template>

        <div class="audit-materials">
          <h3>已提交的信息</h3>
          <dl>
            <dt>企业名称</dt><dd>{{ progress.companyName }}</dd>
            <dt>统一社会信用代码</dt><dd>{{ progress.creditCode }}</dd>
            <dt>注册手机号</dt><dd>{{ progress.phone }}</dd>
            <dt>提交时间</dt><dd>{{ submitTime }}</dd>
            <dt v-if="progress.auditedAtMillis">审核时间</dt>
            <dd v-if="progress.auditedAtMillis">{{ auditTime }}</dd>
          </dl>
          <div v-if="progress.licenseFileId" class="audit-license">
            <span>营业执照</span>
            <img :src="`/api/files/${progress.licenseFileId}`" alt="营业执照"
                 data-testid="audit-license-img" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { authApi, type AuditProgressVO } from '@/api/auth';
import { unwrap } from '@/utils/http';
import {
  AUDIT_PROGRESS_LABELS, fmtDateTime, statusLabel as sharedStatusLabel,
} from '@sent/shared';

const router = useRouter();
const phone = ref('');
const code = ref('');
const error = ref('');
const loading = ref(false);
const countdown = ref(0);
const progress = ref<AuditProgressVO | null>(null);
let countdownTimer: ReturnType<typeof setInterval> | null = null;

const PHONE = /^1[3-9]\d{9}$/;
const status = computed(() => progress.value?.status || '');
/** 进度页文案取共享字典的域内覆盖（审核中 / 已审核 / 审核未通过） */
const statusLabel = computed(() => sharedStatusLabel(status.value, AUDIT_PROGRESS_LABELS));
const statusClass = computed(() => {
  if (status.value === 'PENDING') return 'audit-status--pending';
  if (status.value === 'REJECTED') return 'audit-status--rejected';
  return 'audit-status--pass';
});
const submitTime = computed(() => progress.value?.submittedAtMillis
  ? fmtDateTime(progress.value.submittedAtMillis) : '—');
const auditTime = computed(() => progress.value?.auditedAtMillis
  ? fmtDateTime(progress.value.auditedAtMillis) : '—');

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
  error.value = '';
  if (!PHONE.test(phone.value.trim())) {
    error.value = '请输入正确的 11 位手机号';
    return;
  }
  try {
    const res = await unwrap(authApi.sendCode({ phone: phone.value.trim() }));
    if (import.meta.env.DEV && res?.demoCode) {
      console.info(`[森特演示] 审核进度查询验证码：${res.demoCode}`);
    }
    ElMessage.success('验证码已发送，可在浏览器控制台查看');
    startCountdown();
  } catch (e) {
    error.value = (e as Error).message || '验证码发送失败';
  }
}

async function query(): Promise<void> {
  error.value = '';
  if (!PHONE.test(phone.value.trim())) {
    error.value = '请输入正确的 11 位手机号';
    return;
  }
  if (!/^\d{6}$/.test(code.value.trim())) {
    error.value = '请输入 6 位短信验证码';
    return;
  }
  loading.value = true;
  try {
    progress.value = await unwrap(authApi.enterpriseAuditProgress({
      phone: phone.value.trim(), code: code.value.trim(),
    }));
  } catch (e) {
    progress.value = null;
    error.value = (e as Error).message || '查询失败';
  } finally {
    loading.value = false;
  }
}

onUnmounted(() => {
  if (countdownTimer) clearInterval(countdownTimer);
});
</script>

<style scoped>
.audit-wrap { min-height: 100vh; display: flex; align-items: flex-start; justify-content: center; padding: 36px 16px 60px; background: #f5f7f8; }
.audit-card { position: relative; width: 100%; max-width: 620px; padding: 36px 40px; background: #fff; border-radius: 14px; box-shadow: 0 12px 40px rgba(18, 102, 79, 0.1); }
.audit-back { position: absolute; top: 16px; right: 20px; font-size: 13px; color: var(--sent-muted); }
.audit-card h1 { margin: 0 0 8px; font-size: 26px; text-align: center; color: #1f2d28; }
.audit-sub { margin: 0 0 26px; text-align: center; font-size: 13px; color: var(--sent-muted); }
.audit-form { display: flex; flex-direction: column; gap: 12px; }
.audit-code-row { display: flex; gap: 10px; }
.audit-code-row .el-button { flex: none; }
.audit-error, .ent-field-error { color: #e34d59; font-size: 12px; }
.audit-submit { height: 44px; margin-top: 4px; }
.audit-result { margin-top: 26px; padding-top: 22px; border-top: 1px solid #e9edeb; }
.audit-status { display: inline-block; padding: 6px 16px; border-radius: 20px; font-size: 14px; font-weight: 700; }
.audit-status--pending { background: #fff4e5; color: #c06a16; }
.audit-status--rejected { background: #fdecec; color: #c9383f; }
.audit-status--pass { background: #e7f6ef; color: #12664f; }
.audit-hint { margin: 14px 0; color: var(--sent-muted); font-size: 13px; line-height: 1.7; }
.audit-pass-tip { margin: 14px 0; color: #12664f; font-size: 14px; font-weight: 600; }
.audit-reject { margin: 14px 0; padding: 14px 16px; border-radius: 8px; background: #fdecec; color: #a52a30; font-size: 13px; }
.audit-reject p { margin: 8px 0 0; line-height: 1.7; }
.audit-materials { margin-top: 22px; }
.audit-materials h3 { margin: 0 0 12px; font-size: 15px; color: #33463f; }
.audit-materials dl { display: grid; grid-template-columns: 130px 1fr; gap: 10px 12px; margin: 0; font-size: 13px; }
.audit-materials dt { color: var(--sent-muted); }
.audit-materials dd { margin: 0; color: #33463f; word-break: break-all; }
.audit-license { margin-top: 16px; font-size: 13px; }
.audit-license span { display: block; margin-bottom: 8px; color: var(--sent-muted); }
.audit-license img { max-width: 100%; max-height: 260px; border: 1px solid #e3e8e5; border-radius: 8px; }
</style>
