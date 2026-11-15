<template>
  <div class="ra-page">
    <!-- 返回条 -->
    <div class="ra-bar">
      <button type="button" class="ra-bar__back" @click="goBack">‹ 返回订单详情</button>
      <span class="ra-bar__crumb">我的订单 / 申请售后</span>
    </div>

    <div v-if="loading" class="ra-hint">加载中…</div>
    <el-empty v-else-if="!order" description="订单不存在" />

    <template v-else>
      <!-- 已有售后：不再让人重复申请，直接给进度入口 -->
      <section v-if="existing.length > 0" class="ra-notice">
        <span class="ra-notice__icon">↩</span>
        <div class="ra-notice__body">
          <b>该订单已提交过 {{ existing.length }} 笔售后</b>
          <p>同一笔订单可以多次申请（例如分件退），但请先确认在途申请的处理结果。</p>
        </div>
        <button type="button" class="ra-notice__btn" @click="goRefundList">查看售后进度</button>
      </section>

      <!-- 订单头：单号 + 状态 + 实付 -->
      <section class="ra-order">
        <div class="ra-order__main">
          <span class="ra-order__no">订单号 {{ order.orderNo }}</span>
          <span class="ra-order__status">{{ statusText(order.status) }}</span>
        </div>
        <div class="ra-order__sum">
          实付 <b>¥{{ order.payableYuan || '0.00' }}</b>
        </div>
      </section>

      <!-- 选择要退的商品行 -->
      <section class="ra-card">
        <header class="ra-card__head">
          <h3>选择退款商品</h3>
          <span class="ra-card__tip">可整单退，也可只退其中一件</span>
        </header>

        <div class="ra-lines">
          <label
            v-for="(it, idx) in lines"
            :key="`${it.skuId}-${idx}`"
            class="ra-line"
            :class="{ 'ra-line--on': it.selected, 'ra-line--disabled': !it.refundable }"
          >
            <input
              v-model="it.selected"
              type="checkbox"
              class="ra-line__check"
              :disabled="!it.refundable"
            />
            <ProductThumb class="ra-line__thumb" :name="it.title" :seed="it.skuId || idx" />
            <div class="ra-line__info">
              <div class="ra-line__title">{{ it.title || '商品' }}</div>
              <div class="ra-line__spec">{{ it.spec || '默认规格' }} × {{ it.qty }}</div>
              <div v-if="!it.refundable" class="ra-line__warn">该行无可退金额</div>
            </div>
            <div class="ra-line__money">
              <span class="ra-line__label">可退</span>
              <b>¥{{ fmtCent(it.amountCent) }}</b>
            </div>
          </label>
          <div v-if="lines.length === 0" class="ra-lines__empty">该订单没有可退款的商品行</div>
        </div>
      </section>

      <!-- 退款信息 -->
      <section class="ra-card">
        <header class="ra-card__head">
          <h3>退款信息</h3>
        </header>

        <div class="ra-field">
          <label class="ra-field__label">退款原因</label>
          <el-select v-model="reason" class="ra-field__control" placeholder="请选择退款原因">
            <el-option v-for="r in REASONS" :key="r" :label="r" :value="r" />
          </el-select>
        </div>

        <div class="ra-field">
          <label class="ra-field__label">
            退款凭证 <em class="ra-field__req">必填</em>
          </label>
          <el-input
            v-model="proof"
            class="ra-field__control"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="说明退款理由，例如：外箱破损，已拍照；客服同意退货"
          />
        </div>

        <div class="ra-field ra-field--block">
          <label class="ra-field__label">凭证照片（最多 6 张）</label>
          <el-upload
            v-model:file-list="files"
            list-type="picture-card"
            accept="image/*"
            :auto-upload="false"
            :limit="MAX_IMAGES"
            :on-exceed="onExceed"
          >
            <span class="ra-upload__add">＋</span>
          </el-upload>
          <p class="ra-field__hint">照片会随退款单一起提交给客服核验；不传也能提交，但审核会更慢。</p>
        </div>
      </section>

      <!-- 底部结算条 -->
      <div class="ra-submit">
        <div class="ra-submit__sum">
          已选 <b>{{ selectedCount }}</b> 件，合计可退
          <b class="ra-submit__money">¥{{ fmtCent(totalCent) }}</b>
        </div>
        <button
          type="button"
          class="ra-submit__btn"
          :disabled="acting || selectedCount === 0"
          @click="submit"
        >
          {{ acting ? '提交中…' : '提交申请' }}
        </button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 按订单申请退款 / 售后（2026-11-16）。
 *
 * 为什么单独做一页：订单详情的「订单服务 › 退款/售后」原来直接跳到「我的售后」列表，
 * 用户还得在列表里找自己刚才那单，再想办法点进去申请——路径完全反了。
 * 淘宝的做法是「这一单 → 这一单的售后页」，本页就是那个页面。
 *
 * 关键口径（与后端 `POST /api/refunds` 一致）：
 * - 只能按**订单真实商品行**退：`lines[].skuId` 必须能在 `order_item` 里找到；
 * - 行金额用**行实付** `linePaidCent`（= 行金额 − 分摊优惠），不是应付总额，
 *   否则多行一起退会超退（后端返回 40940 REFUND_OVERFLOW）；
 * - 凭证 `proof` 后端必填，前端先拦一次，避免让用户看 400 的控制台红字；
 * - 图片先传文件服务拿 fileId，再随退款单提交（二进制不进 JSON）。
 */
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { UploadUserFile } from 'element-plus';
import { fmtCent } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { orderApi, type OrderVO } from '@/api/order';
import { refundApi, type RefundVO } from '@/api/refund';
import { fileApi } from '@/api/file';
import ProductThumb from '@/components/ProductThumb.vue';

const route = useRoute();
const router = useRouter();

const REASONS = ['七天无理由退货', '收到商品破损', '商品与描述不符', '发错货 / 少发货', '质量问题', '其他原因'];
const MAX_IMAGES = 6;
const MAX_IMAGE_BYTES = 20 * 1024 * 1024;

interface RefundLineView {
  skuId: number;
  title: string;
  spec: string;
  qty: number;
  amountCent: number;
  refundable: boolean;
  selected: boolean;
}

const loading = ref(false);
const acting = ref(false);
const order = ref<OrderVO | null>(null);
const existing = ref<RefundVO[]>([]);
const lines = ref<RefundLineView[]>([]);
const reason = ref(REASONS[0]);
const proof = ref('');
const files = ref<UploadUserFile[]>([]);
/** uid（缺省退回文件名）→ fileId：重试时不必重复上传同一个文件 */
const uploadedIds = new Map<string, string>();

const STATUS_TEXT: Record<string, string> = {
  WAIT_PAY: '待付款',
  WAIT_SHIP: '待发货',
  WAIT_RECEIVE: '待收货',
  PART_SHIPPED: '部分发货',
  COMPLETED: '交易完成',
  CANCELLED: '已取消',
  CLOSED: '已关闭',
};

function statusText(status: string | null | undefined): string {
  return STATUS_TEXT[status ?? ''] ?? '处理中';
}

const selectedCount = computed(() => lines.value.filter((l) => l.selected).length);
const totalCent = computed(() =>
  lines.value.filter((l) => l.selected).reduce((sum, l) => sum + l.amountCent, 0),
);

function goBack(): void {
  router.back();
}
function goRefundList(): void {
  router.push('/refunds');
}
function onExceed(): void {
  ElMessage.warning(`最多上传 ${MAX_IMAGES} 张凭证照片`);
}

/** 先把选中的照片传到文件服务，拿 fileId；图片不塞进退款请求体 */
async function uploadImages(): Promise<string[]> {
  const ids: string[] = [];
  for (const file of files.value) {
    const uid = String(file.uid ?? file.name);
    const cached = uploadedIds.get(uid);
    if (cached) {
      ids.push(cached);
      continue;
    }
    if (!file.raw) continue;
    if (file.raw.size > MAX_IMAGE_BYTES) {
      throw new Error(`照片「${file.name}」超过 20MB，请压缩后再传`);
    }
    const uploaded = await unwrap(fileApi.upload(file.raw));
    uploadedIds.set(uid, uploaded.fileId);
    ids.push(uploaded.fileId);
  }
  return ids;
}

async function submit(): Promise<void> {
  if (!order.value) return;
  const picked = lines.value.filter((l) => l.selected);
  if (picked.length === 0) {
    ElMessage.warning('请至少选择一件要退款的商品');
    return;
  }
  if (!proof.value.trim()) {
    ElMessage.warning('请填写退款凭证');
    return;
  }
  acting.value = true;
  try {
    const images = await uploadImages();
    const res = await unwrap(refundApi.apply({
      orderNo: order.value.orderNo,
      lines: picked.map((l) => ({ skuId: l.skuId, amountCent: l.amountCent })),
      reason: reason.value,
      proof: proof.value.trim(),
      images,
    }));
    ElMessage.success('退款申请已提交，客服会尽快处理');
    if (res?.refundNo) {
      router.replace(`/refund/${res.refundNo}`);
      return;
    }
    router.replace('/refunds');
  } catch (e) {
    ElMessage.error((e as Error).message || '申请失败');
  } finally {
    acting.value = false;
  }
}

/** 该订单已有的售后单：只用来提示「已申请过」，不挡重复申请（分件退是合理需求） */
async function loadExisting(orderNo: string): Promise<void> {
  try {
    const page = await unwrap(refundApi.list({ page: 1, size: 50 }));
    existing.value = (page?.list ?? []).filter((r) => r.orderNo === orderNo);
  } catch {
    existing.value = [];
  }
}

onMounted(async () => {
  const orderNo = String(route.params.orderNo ?? '');
  if (!orderNo) {
    order.value = null;
    return;
  }
  loading.value = true;
  try {
    const vo = await unwrap(orderApi.detail(orderNo));
    order.value = vo;
    lines.value = (vo.items ?? []).map((it) => {
      // 行金额 = 行实付（订单金额 − 分摊优惠）；缺失才退回「单价 × 数量」
      const amount = Number(it.linePaidCent ?? Number(it.unitPriceCent ?? 0) * Number(it.qty ?? 0));
      const skuId = Number(it.skuId ?? 0);
      return {
        skuId,
        title: it.title ?? '商品',
        spec: it.spec ?? '默认规格',
        qty: Number(it.qty ?? 1),
        amountCent: amount > 0 ? amount : 0,
        refundable: skuId > 0 && amount > 0,
        selected: skuId > 0 && amount > 0,
      };
    });
    await loadExisting(orderNo);
  } catch {
    order.value = null;
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.ra-page {
  width: var(--sent-container);
  max-width: calc(100% - 32px);
  margin: 0 auto;
  padding: 12px 0 90px;
}

/* ---------- 返回条 ---------- */
.ra-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.ra-bar__back {
  border: none;
  background: none;
  font-size: 13px;
  color: var(--sent-text-2);
  cursor: pointer;
  padding: 0;
}
.ra-bar__back:hover {
  color: var(--sent-primary);
}
.ra-bar__crumb {
  font-size: 12px;
  color: var(--sent-muted);
}

.ra-hint {
  padding: 40px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}

/* ---------- 已有售后提示 ---------- */
.ra-notice {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff8ee;
  border: 1px solid #ffe0bd;
  border-radius: 10px;
  padding: 12px 16px;
  margin-bottom: 12px;
}
.ra-notice__icon {
  font-size: 20px;
  color: var(--sent-accent-dark);
}
.ra-notice__body {
  flex: 1;
  min-width: 0;
}
.ra-notice__body b {
  font-size: 13px;
  color: var(--sent-text);
}
.ra-notice__body p {
  margin: 3px 0 0;
  font-size: 12px;
  color: var(--sent-muted);
}
.ra-notice__btn {
  flex: none;
  border: 1px solid var(--sent-accent);
  background: #fff;
  color: var(--sent-accent-dark);
  border-radius: 14px;
  padding: 5px 14px;
  font-size: 12px;
  cursor: pointer;
}
.ra-notice__btn:hover {
  background: var(--sent-accent);
  color: #fff;
}

/* ---------- 订单头 ---------- */
.ra-order {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  background: #fff;
  border-radius: 10px;
  padding: 14px 18px;
  margin-bottom: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.ra-order__main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.ra-order__no {
  font-size: 13px;
  color: var(--sent-text);
  font-variant-numeric: tabular-nums;
}
.ra-order__status {
  font-size: 11px;
  color: var(--sent-primary);
  background: var(--sent-primary-soft);
  border-radius: 10px;
  padding: 2px 8px;
}
.ra-order__sum {
  font-size: 13px;
  color: var(--sent-muted);
  flex: none;
}
.ra-order__sum b {
  font-size: 16px;
  color: var(--sent-accent-dark);
  margin-left: 4px;
}

/* ---------- 通用卡片 ---------- */
.ra-card {
  background: #fff;
  border-radius: 10px;
  padding: 16px 18px;
  margin-bottom: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.ra-card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 12px;
}
.ra-card__head h3 {
  margin: 0;
  font-size: 15px;
  color: var(--sent-text);
  padding-left: 10px;
  position: relative;
}
.ra-card__head h3::before {
  content: '';
  position: absolute;
  left: 0;
  top: 3px;
  bottom: 3px;
  width: 3px;
  border-radius: 2px;
  background: var(--sent-accent);
}
.ra-card__tip {
  font-size: 12px;
  color: var(--sent-muted);
}

/* ---------- 商品行 ---------- */
.ra-lines {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ra-line {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid var(--sent-border);
  border-radius: 8px;
  padding: 10px 14px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.ra-line:hover {
  border-color: var(--sent-primary);
}
.ra-line--on {
  border-color: var(--sent-primary);
  background: #f7fbf9;
}
.ra-line--disabled {
  cursor: not-allowed;
  opacity: 0.6;
}
.ra-line__check {
  width: 16px;
  height: 16px;
  accent-color: var(--sent-primary);
  flex: none;
}
.ra-line__thumb {
  width: 56px;
  height: 56px;
  border-radius: 6px;
  flex: none;
}
/* 56px 的小图放不下占位文案：只留 emoji，否则文字会从方框里溢出来 */
.ra-line__thumb :deep(.thumb__ph) {
  gap: 0;
  padding: 0;
  justify-content: center;
}
.ra-line__thumb :deep(.thumb__emoji) {
  font-size: 20px;
}
.ra-line__thumb :deep(.thumb__label) {
  display: none;
}
.ra-line__info {
  flex: 1;
  min-width: 0;
}
.ra-line__title {
  font-size: 13px;
  color: var(--sent-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ra-line__spec {
  margin-top: 4px;
  font-size: 12px;
  color: var(--sent-muted);
}
.ra-line__warn {
  margin-top: 4px;
  font-size: 12px;
  color: #e34d59;
}
.ra-line__money {
  flex: none;
  text-align: right;
}
.ra-line__label {
  display: block;
  font-size: 11px;
  color: var(--sent-muted);
}
.ra-line__money b {
  font-size: 15px;
  color: var(--sent-accent-dark);
}
.ra-lines__empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
}

/* ---------- 表单 ---------- */
.ra-field {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 10px 0;
  border-bottom: 1px dashed #f0f2f4;
}
.ra-field:last-child {
  border-bottom: none;
}
.ra-field--block {
  flex-direction: column;
  gap: 8px;
}
/* 块级字段的标签不该再占 96px 固定宽，否则「凭证照片（最多 6 张）」会被折成两行 */
.ra-field--block .ra-field__label {
  width: auto;
  line-height: 1.5;
}
.ra-field__label {
  flex: none;
  width: 96px;
  font-size: 13px;
  color: var(--sent-text-2);
  line-height: 32px;
}
.ra-field__req {
  font-style: normal;
  color: #e34d59;
  font-size: 11px;
  margin-left: 2px;
}
.ra-field__control {
  flex: 1;
  min-width: 0;
}
.ra-field__hint {
  margin: 0;
  font-size: 12px;
  color: var(--sent-muted);
}
.ra-upload__add {
  font-size: 22px;
  color: #b9bfc4;
}

/* ---------- 底部提交条 ---------- */
.ra-submit {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 18px;
  background: #fff;
  border-top: 1px solid var(--sent-border);
  padding: 12px 24px;
  box-shadow: 0 -2px 10px rgba(0, 0, 0, 0.05);
  z-index: 20;
}
.ra-submit__sum {
  font-size: 13px;
  color: var(--sent-text-2);
}
.ra-submit__sum b {
  color: var(--sent-text);
}
.ra-submit__money {
  font-size: 18px;
  color: var(--sent-accent-dark) !important;
  margin-left: 2px;
}
.ra-submit__btn {
  border: none;
  background: var(--sent-accent);
  color: #fff;
  border-radius: 20px;
  padding: 9px 34px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.15s;
}
.ra-submit__btn:hover {
  background: var(--sent-accent-dark);
}
.ra-submit__btn:disabled {
  background: #d6d9de;
  cursor: not-allowed;
}

@media (max-width: 1080px) {
  .ra-field {
    flex-direction: column;
    gap: 6px;
  }
  .ra-field__label {
    width: auto;
    line-height: 1.5;
  }
}
</style>
