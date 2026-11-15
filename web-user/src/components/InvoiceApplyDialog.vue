<template>
  <el-dialog
    :model-value="modelValue"
    title="申请开票"
    width="480px"
    append-to-body
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @open="onOpen"
  >
    <div v-if="loading" class="inv-dialog__hint">加载中…</div>

    <!-- 已提交 / 已开具：不再给表单，避免同一订单开出第二张票 -->
    <div v-else-if="current" class="inv-dialog__state">
      <div class="inv-dialog__badge" :class="`inv-dialog__badge--${current.status === 'ISSUED' ? 'done' : 'wait'}`">
        {{ stateText(current.status) }}
      </div>
      <div class="inv-dialog__row"><span>发票抬头</span><b>{{ current.title || '—' }}</b></div>
      <div class="inv-dialog__row"><span>票面金额</span><b class="inv-dialog__money">¥{{ current.amountYuan || '0.00' }}</b></div>
      <div class="inv-dialog__row"><span>发票类型</span><b>{{ current.type === 'SPECIAL' ? '增值税专用发票' : '增值税普通发票' }}</b></div>
      <div v-if="current.taxNo" class="inv-dialog__row"><span>纳税人识别号</span><b>{{ current.taxNo }}</b></div>
      <div v-if="current.invoiceNo" class="inv-dialog__row"><span>发票号码</span><b>{{ current.invoiceNo }}</b></div>
      <div v-if="current.pdfUrl" class="inv-dialog__row">
        <span>电子发票</span>
        <a class="inv-dialog__pdf" :href="current.pdfUrl" target="_blank" rel="noopener">下载 PDF</a>
      </div>
      <p class="inv-dialog__tip">
        {{ current.status === 'ISSUED'
          ? '发票已开具，可在「我的账户 — 发票管理」查看历史记录。'
          : '申请已提交，财务开具后会以短信/邮件通知，可在发票管理中下载。' }}
      </p>
    </div>

    <div v-else class="inv-dialog__form">
      <div class="inv-dialog__row inv-dialog__row--form">
        <span>发票类型</span>
        <div class="inv-dialog__types">
          <button
            v-for="t in TYPES"
            :key="t.value"
            type="button"
            class="inv-dialog__type"
            :class="{ 'inv-dialog__type--on': form.type === t.value }"
            @click="pickType(t.value)"
          >{{ t.label }}</button>
        </div>
      </div>
      <div class="inv-dialog__field">
        <label>发票抬头</label>
        <el-input v-model="form.title" maxlength="128" placeholder="请输入发票抬头（个人姓名或公司名称）" />
      </div>
      <div class="inv-dialog__field">
        <label>纳税人识别号{{ form.type === 'SPECIAL' ? '' : '（选填）' }}</label>
        <el-input v-model="form.taxNo" maxlength="64" placeholder="纳税人识别号（专用发票必填）" />
      </div>
      <div class="inv-dialog__field">
        <label>接收邮箱（选填）</label>
        <el-input v-model="form.email" maxlength="128" placeholder="接收电子发票的邮箱" />
      </div>
      <p class="inv-dialog__tip">
        票面金额按订单实付金额开具（¥{{ amountYuan }}），一个订单只能开一张发票。
      </p>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button
        v-if="!loading && !current"
        type="primary"
        :loading="submitting"
        @click="submit"
      >提交申请</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 申请开票弹窗（订单列表 / 订单详情共用）。
 *
 * 三种形态：
 * 1) 从未申请 → 表单（类型 / 抬头 / 税号 / 邮箱）；
 * 2) 已提交（APPLIED）→ 只读状态，不再给表单，避免重复开票；
 * 3) 已开具（ISSUED）→ 只读状态 + 下载 PDF。
 *
 * 状态以 `GET /api/invoices/order/{orderNo}` 为准：打开弹窗先查一次，
 * 提交成功后再查一次，父组件通过 `updated` 事件同步页面上的 `.od-invoice` 区块。
 */
import { computed, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { fmtCent } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { invoiceApi, type InvoiceType, type InvoiceVO } from '@/api/invoice';

const props = defineProps<{
  modelValue: boolean;
  orderNo: string;
  /** 订单实付（分），仅用于表单里的金额提示 */
  payableCent?: number | null;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void;
  /** 申请成功 / 状态变化，父组件据此刷新发票区块 */
  (e: 'updated', vo: InvoiceVO | null): void;
}>();

const TYPES: { value: InvoiceType; label: string }[] = [
  { value: 'NORMAL', label: '增值税普通发票' },
  { value: 'SPECIAL', label: '增值税专用发票' },
];

const loading = ref(false);
const submitting = ref(false);
const current = ref<InvoiceVO | null>(null);
const form = reactive({ type: 'NORMAL' as InvoiceType, title: '', taxNo: '', email: '' });

const amountYuan = computed(() => fmtCent(Number(props.payableCent ?? 0)));

function stateText(status: string | null | undefined): string {
  if (status === 'ISSUED') return '已开具';
  if (status === 'APPLIED') return '已提交，待开具';
  return '处理中';
}

function pickType(v: InvoiceType): void {
  form.type = v;
}

async function onOpen(): Promise<void> {
  current.value = null;
  await refresh();
}

async function refresh(): Promise<void> {
  if (!props.orderNo) {
    return;
  }
  loading.value = true;
  try {
    const vo = await unwrap(invoiceApi.latest(props.orderNo));
    current.value = vo ?? null;
    if (vo) {
      // 已申请过的单把原抬头回填进表单，用户想换抬头重开时不至于空白
      form.type = (vo.type as InvoiceType) ?? 'NORMAL';
      form.title = vo.title ?? '';
      form.taxNo = vo.taxNo ?? '';
      form.email = vo.email ?? '';
    }
    emit('updated', current.value);
  } catch (e) {
    // 查不到状态不该阻断开票：订单刚支付完、或接口暂无记录时按「未申请」处理
    current.value = null;
    const msg = (e as Error).message;
    if (msg) {
      ElMessage.warning(msg);
    }
  } finally {
    loading.value = false;
  }
}

async function submit(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请填写发票抬头');
    return;
  }
  if (form.type === 'SPECIAL' && !form.taxNo.trim()) {
    ElMessage.warning('开具增值税专用发票必须填写纳税人识别号');
    return;
  }
  submitting.value = true;
  try {
    const vo = await unwrap(invoiceApi.apply({
      orderNo: props.orderNo,
      title: form.title.trim(),
      taxNo: form.taxNo.trim() || undefined,
      type: form.type,
      email: form.email.trim() || undefined,
    }));
    current.value = vo ?? null;
    ElMessage.success('开票申请已提交');
    emit('updated', current.value);
  } catch (e) {
    ElMessage.error((e as Error).message || '申请失败');
  } finally {
    submitting.value = false;
  }
}

watch(
  () => props.orderNo,
  () => {
    // 换单时清空上一条状态，避免闪一下别人的发票
    current.value = null;
  },
);
</script>

<style scoped>
.inv-dialog__hint {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}
.inv-dialog__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  font-size: 13px;
  color: var(--sent-text-2);
  padding: 7px 0;
  border-bottom: 1px dashed #f2f3f5;
}
.inv-dialog__row b {
  color: var(--sent-text);
  font-weight: 600;
  text-align: right;
  word-break: break-all;
}
.inv-dialog__row--form {
  border-bottom: none;
  padding-bottom: 2px;
}
.inv-dialog__badge {
  display: inline-block;
  margin-bottom: 10px;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}
.inv-dialog__badge--wait {
  background: #fff7e6;
  color: #d46b08;
}
.inv-dialog__badge--done {
  background: var(--sent-primary-soft);
  color: var(--sent-primary);
}
.inv-dialog__money {
  color: var(--sent-accent-dark) !important;
}
.inv-dialog__pdf {
  color: var(--sent-primary);
  text-decoration: none;
  font-weight: 600;
}
.inv-dialog__pdf:hover {
  text-decoration: underline;
}
.inv-dialog__types {
  display: flex;
  gap: 8px;
}
.inv-dialog__type {
  border: 1px solid #e5e6eb;
  background: #fff;
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 13px;
  color: var(--sent-text-2);
  cursor: pointer;
  transition: all 0.15s;
}
.inv-dialog__type--on {
  border-color: var(--sent-primary);
  color: var(--sent-primary);
  background: var(--sent-primary-soft);
  font-weight: 600;
}
.inv-dialog__field {
  margin-top: 12px;
}
.inv-dialog__field label {
  display: block;
  font-size: 12px;
  color: var(--sent-muted);
  margin-bottom: 5px;
}
.inv-dialog__tip {
  margin: 12px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--sent-muted);
}
</style>
