<template>
  <div class="ent-register-wrap">
    <div class="ent-register-card">
      <router-link to="/enterprise/login" class="ent-register-back" data-testid="back-to-enterprise-login">
        返回企业登录 ›
      </router-link>
      <h1>企业账号注册</h1>

      <ol class="ent-steps">
        <li :class="{ on: submitted !== true }"><b>1</b><span>企业账号注册</span></li>
        <li :class="{ on: submitted }"><b>2</b><span>信息审核</span></li>
      </ol>

      <template v-if="!submitted">
        <el-form :model="form" label-position="top" @submit.prevent>
          <el-form-item label="营业执照" required :error="errors.license">
            <label class="license-upload" data-testid="enterprise-license-upload">
              <input
                type="file"
                accept="image/jpeg,image/png"
                data-testid="enterprise-license-input"
                @change="onLicenseChange"
              />
              <span class="license-upload__icon">⬆</span>
              <b>{{ licenseName || '点击按钮或拖拽到此区域以上传' }}</b>
              <small>请上传企业名称 & 社会信用代码无遮挡版本，支持 JPG / PNG</small>
              <em>上传营业执照</em>
            </label>
          </el-form-item>

          <el-form-item label="企业名称" required :error="errors.company">
            <el-input v-model="form.companyName" placeholder="请输入公司全称" data-testid="enterprise-company" clearable />
          </el-form-item>
          <el-form-item label="企业社会信用代码" required :error="errors.credit">
            <el-input v-model="form.creditCode" maxlength="18" placeholder="请输入完整代码"
                      data-testid="enterprise-credit-code" clearable />
          </el-form-item>
          <el-form-item label="手机号" required :error="errors.phone">
            <el-input v-model="form.phone" maxlength="11" placeholder="请输入手机号"
                      data-testid="enterprise-phone" clearable />
          </el-form-item>
          <el-form-item label="验证码" required :error="errors.code">
            <div class="code-row">
              <el-input v-model="form.code" maxlength="6" placeholder="请输入 6 位短信验证码"
                        data-testid="enterprise-code" />
              <el-button data-testid="enterprise-send-code" :disabled="countdown > 0" @click="sendCode">
                {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
              </el-button>
            </div>
          </el-form-item>
          <el-form-item label="登录密码" required :error="errors.password">
            <el-input v-model="form.password" type="password" show-password placeholder="6~20 位登录密码"
                      data-testid="enterprise-password" />
          </el-form-item>
          <el-form-item label="确认密码" required :error="errors.confirm">
            <el-input v-model="form.confirmPassword" type="password" show-password placeholder="请再次输入密码"
                      data-testid="enterprise-confirm-password" />
          </el-form-item>

          <el-checkbox v-model="form.agreed" class="ent-agree" data-testid="enterprise-agree">
            我已阅读并同意《森特商城企业用户服务协议》和《隐私权政策》
          </el-checkbox>
          <div v-if="errors.agree" class="ent-field-error" data-testid="enterprise-agree-error">{{ errors.agree }}</div>

          <el-button type="primary" class="ent-submit" :loading="loading"
                     data-testid="enterprise-register-submit" @click="submit">立即注册</el-button>
        </el-form>
      </template>

      <div v-else class="ent-submitted" data-testid="enterprise-register-success">
        <b>资料已提交，等待管理端审核</b>
        <p>客户号由系统自动分配，您无需记录。审核通过后使用注册手机号和密码登录即可。</p>
        <el-button type="primary" @click="router.replace('/enterprise/login')">前往企业登录</el-button>
        <el-button link type="primary" @click="router.push('/enterprise/audit-progress')">查看审核进度</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { authApi } from '@/api/auth';
import { fileApi } from '@/api/file';
import { unwrap } from '@/utils/http';

const router = useRouter();
const PHONE = /^1[3-9]\d{9}$/;
const CREDIT_CODE = /^[0-9A-HJ-NPQRTUWXY]{18}$/;

const form = reactive({
  phone: '', password: '', confirmPassword: '', companyName: '', creditCode: '',
  code: '', agreed: false,
});
const errors = reactive({
  license: '', company: '', credit: '', phone: '', code: '',
  password: '', confirm: '', agree: '',
});
const loading = ref(false);
const submitted = ref(false);
const licenseFileId = ref('');
const licenseName = ref('');
const countdown = ref(0);
let countdownTimer: ReturnType<typeof setInterval> | null = null;

function clearErrors(): void {
  Object.keys(errors).forEach((key) => { (errors as Record<string, string>)[key] = ''; });
}

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
  errors.phone = '';
  if (!PHONE.test(form.phone.trim())) {
    errors.phone = '请输入正确的 11 位手机号';
    return;
  }
  try {
    const res = await unwrap(authApi.sendCode({ phone: form.phone.trim() }));
    if (import.meta.env.DEV && res?.demoCode) {
      console.info(`[森特演示] 企业注册验证码：${res.demoCode}`);
    }
    ElMessage.success('验证码已发送，可在浏览器控制台查看');
    startCountdown();
  } catch (e) {
    errors.code = (e as Error).message || '验证码发送失败';
  }
}

/** 与后端 LicenseImageValidator 同口径：尺寸过小或拉普拉斯方差过低即视为模糊。 */
async function detectBlur(file: File): Promise<string | null> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(url);
      if (image.width < 320 || image.height < 240) {
        resolve('营业执照图片尺寸过小，请上传清晰照片');
        return;
      }
      const size = 100;
      const canvas = document.createElement('canvas');
      canvas.width = size;
      canvas.height = size;
      const ctx = canvas.getContext('2d');
      if (!ctx) { resolve(null); return; }
      ctx.drawImage(image, 0, 0, size, size);
      const data = ctx.getImageData(0, 0, size, size).data;
      const gray: number[] = [];
      for (let i = 0; i < data.length; i += 4) {
        gray.push(Math.round(0.299 * data[i] + 0.587 * data[i + 1] + 0.114 * data[i + 2]));
      }
      let sum = 0;
      let sumSq = 0;
      let count = 0;
      for (let y = 1; y < size - 1; y++) {
        for (let x = 1; x < size - 1; x++) {
          const i = y * size + x;
          const lap = 4 * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - size] - gray[i + size];
          sum += lap;
          sumSq += lap * lap;
          count++;
        }
      }
      const mean = sum / count;
      const variance = sumSq / count - mean * mean;
      resolve(variance < 80 ? '营业执照图片模糊，请重新上传清晰照片' : null);
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      resolve('营业执照图片无法识别，请上传 JPG / PNG');
    };
    image.src = url;
  });
}

async function onLicenseChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  errors.license = '';
  if (!/\.(jpe?g|png)$/i.test(file.name)) {
    errors.license = '营业执照仅支持 JPG / PNG';
    input.value = '';
    return;
  }
  const blurError = await detectBlur(file);
  if (blurError) {
    errors.license = blurError;
    licenseFileId.value = '';
    input.value = '';
    return;
  }
  loading.value = true;
  try {
    const stored = await unwrap(fileApi.uploadEnterpriseLicense(file));
    if (!stored?.fileId) throw new Error('上传失败');
    licenseFileId.value = stored.fileId;
    licenseName.value = stored.fileName || file.name;
    ElMessage.success('营业执照已上传');
  } catch (e) {
    errors.license = (e as Error).message || '营业执照上传失败';
  } finally {
    loading.value = false;
    input.value = '';
  }
}

function validate(): boolean {
  clearErrors();
  if (!licenseFileId.value) errors.license = '请上传清晰的营业执照';
  if (!form.companyName.trim()) errors.company = '请输入企业名称';
  else if (form.companyName.trim().length > 128) errors.company = '企业名称不能超过 128 字';
  if (!CREDIT_CODE.test(form.creditCode.trim().toUpperCase())) errors.credit = '统一社会信用代码应为 18 位';
  if (!PHONE.test(form.phone.trim())) errors.phone = '请输入正确的 11 位手机号';
  if (!/^\d{6}$/.test(form.code.trim())) errors.code = '请输入 6 位短信验证码';
  if (form.password.length < 6 || form.password.length > 20) errors.password = '密码长度需为 6~20 位';
  if (form.password !== form.confirmPassword) errors.confirm = '两次输入的密码不一致';
  if (!form.agreed) errors.agree = '请阅读并同意协议';
  return !Object.values(errors).some(Boolean);
}

/** 后端业务错误映射到具体字段，保证红字出现在出错的输入框下。 */
function mapServerError(message: string): void {
  if (message.includes('验证码')) errors.code = message;
  else if (message.includes('信用代码')) errors.credit = message;
  else if (message.includes('营业执照') || message.includes('图片')) errors.license = message;
  else if (message.includes('企业名')) errors.company = message;
  else if (message.includes('手机号')) errors.phone = message;
  else if (message.includes('密码')) errors.password = message;
  else ElMessage.error(message);
}

async function submit(): Promise<void> {
  if (!validate()) return;
  loading.value = true;
  try {
    await unwrap(authApi.enterpriseRegister({
      phone: form.phone.trim(),
      password: form.password,
      confirmPassword: form.confirmPassword,
      companyName: form.companyName.trim(),
      creditCode: form.creditCode.trim().toUpperCase(),
      licenseFileId: licenseFileId.value,
      code: form.code.trim(),
    }));
    submitted.value = true;
  } catch (e) {
    const err = e as Error & { code?: number };
    if (err.code === 42201) {
      errors.code = '该手机号已注册，请直接登录或查看审核进度';
    } else {
      mapServerError(err.message || '注册失败');
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
.ent-register-wrap {
  min-height: 100vh; display: flex; align-items: flex-start; justify-content: center;
  padding: 36px 16px 60px; background: #f5f7f8;
}
.ent-register-card {
  position: relative; width: 100%; max-width: 620px; padding: 34px 40px;
  background: #fff; border-radius: 14px; box-shadow: 0 12px 40px rgba(18, 102, 79, 0.1);
}
.ent-register-back { position: absolute; top: 16px; right: 20px; font-size: 13px; color: var(--sent-muted); }
.ent-register-card h1 { margin: 0 0 22px; font-size: 26px; text-align: center; color: #1f2d28; }
.ent-steps { display: flex; align-items: center; gap: 12px; list-style: none; padding: 0; margin: 0 0 26px; }
.ent-steps li { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6px; color: #aab2b8; font-size: 13px; }
.ent-steps b {
  width: 28px; height: 28px; border-radius: 50%; display: grid; place-items: center;
  background: #e6e9ec; color: #8a94a6;
}
.ent-steps li.on { color: #e36f19; font-weight: 600; }
.ent-steps li.on b { background: #ff7a1a; color: #fff; }
.license-upload {
  position: relative; display: flex; flex-direction: column; align-items: center; gap: 6px;
  width: 100%; padding: 22px; border: 1px dashed #cfd8d4; border-radius: 10px;
  background: #fafcfb; cursor: pointer; text-align: center;
}
.license-upload input { position: absolute; inset: 0; opacity: 0; cursor: pointer; width: 100%; }
.license-upload__icon {
  width: 28px; height: 28px; display: grid; place-items: center;
  border-radius: 8px; background: #ff7a1a; color: #fff; font-size: 18px;
}
.license-upload b { font-size: 13px; color: #33463f; }
.license-upload small { font-size: 11px; color: #9aa5a0; }
.license-upload em {
  padding: 6px 16px; border-radius: 8px; background: #ff7a1a; color: #fff;
  font-style: normal; font-size: 12px;
}
.code-row { display: flex; gap: 10px; width: 100%; }
.code-row .el-button { flex: none; }
.ent-agree { margin-bottom: 6px !important; }
.ent-agree :deep(.el-checkbox__label) { font-size: 12px; line-height: 1.6; color: var(--sent-muted); }
.ent-field-error { margin: 0 0 12px; color: #e34d59; font-size: 12px; }
.ent-submit { width: 100%; height: 44px; font-size: 16px; letter-spacing: 3px; }
.ent-submitted { padding: 30px 10px; text-align: center; }
.ent-submitted b { display: block; margin-bottom: 10px; color: #12664f; font-size: 17px; }
.ent-submitted p { margin: 0 0 20px; color: var(--sent-muted); font-size: 13px; line-height: 1.7; }
</style>
