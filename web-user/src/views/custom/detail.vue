<template>
  <div v-loading="loading" class="container custom-detail">
    <!-- 顶部：返回 + 单号 + 状态 -->
    <div class="custom-detail__bar">
      <a class="custom-detail__back" @click="back">‹ 返回我的定制</a>
      <span class="custom-detail__no">{{ detail?.customOrderId || customOrderId }}</span>
      <el-tag
        v-if="detail"
        class="custom-detail__status"
        size="small"
        :type="tone(detail.status)"
      >{{ label(detail.status) }}</el-tag>
    </div>

    <el-empty v-if="!loading && !detail" description="未找到该定制单" :image-size="80" />

    <template v-if="detail">
      <!-- 状态提示：告诉用户现在该做什么 -->
      <div class="detail-hint" :class="`detail-hint--${hint.tone}`">{{ hint.text }}</div>

      <!-- 基础信息 -->
      <div class="sent-card custom-detail__info">
        <div class="info-row">
          <span>定制单号</span><b>{{ detail.customOrderId }}</b>
        </div>
        <div class="info-row">
          <span>关联订单</span>
          <span>
            <a v-if="detail.orderNo" class="info-link" @click="goOrder">{{ detail.orderNo }}</a>
            <i v-else>—</i>
            <em v-if="detail.orderStatus" class="info-sub">{{ label(detail.orderStatus) }}</em>
          </span>
        </div>
        <div class="info-row">
          <span>图稿版本</span><span>v{{ detail.version }}（共 {{ drafts.length }} 稿）</span>
        </div>
        <div class="info-row">
          <span>驳回次数</span><span :class="{ danger: Number(detail.rejectCount) > 0 }">{{ detail.rejectCount }} 次</span>
        </div>
        <div v-if="detail.rejectReason" class="info-row">
          <span>驳回原因</span><span class="danger">{{ detail.rejectReason }}</span>
        </div>
        <div class="info-row">
          <span>定制数量</span><span>{{ detail.qty || 0 }} 件</span>
        </div>
        <div class="info-row">
          <span>定制费</span><span>{{ fmtMoney(detail.feeCent) }}</span>
        </div>
        <div class="info-row">
          <span>印刷面</span><span>{{ sideLabel(detail.printSide) }}</span>
        </div>
        <div class="info-row">
          <span>定制类型</span><span>{{ serviceLabel(detail.serviceType) }}</span>
        </div>
        <div class="info-row">
          <span>下单时间</span><span>{{ timeText(detail.createdAtMillis) }}</span>
        </div>
        <div class="info-row">
          <span>审核截止</span><span>{{ timeText(detail.reviewDeadlineMillis) }}</span>
        </div>
        <div class="info-row">
          <span>预计发货</span><span>{{ timeText(detail.expectShipDateMillis) }}</span>
        </div>
      </div>

      <!-- 图稿版本 -->
      <div class="sent-card">
        <div class="list-head">
          <strong>图稿版本</strong>
          <span class="list-head__sub">
            {{ drafts.length }} 个版本{{ drafts.length > 0 ? '，点击图片可放大' : '' }}
          </span>
        </div>

        <div v-if="drafts.length === 0" class="draft-empty">
          还没有上传图稿，点击下方按钮上传设计稿（JPG / PNG）
        </div>
        <div v-else class="draft-list">
          <div
            v-for="(d, i) in drafts"
            :key="d.version"
            class="draft-item"
            :class="{ 'draft-item--current': Number(d.version) === Number(detail.version) }"
          >
            <el-image
              class="draft-item__img"
              :src="fileUrl(d.fileId)"
              :preview-src-list="previewList"
              :initial-index="i"
              fit="cover"
              preview-teleported
            />
            <div class="draft-item__meta">
              <b>v{{ d.version }}</b>
              <span v-if="Number(d.version) === Number(detail.version)" class="draft-item__badge">当前</span>
              <span class="draft-item__time">{{ timeText(d.uploadedAtMillis) }}</span>
            </div>
          </div>
        </div>

        <input
          ref="fileInput"
          type="file"
          accept="image/*"
          class="draft-input"
          @change="onPickFile"
        />
        <el-button
          class="up-btn"
          type="primary"
          :loading="uploading"
          :disabled="!canUpload"
          @click="pickFile"
        >
          {{ canUpload ? (drafts.length === 0 ? '上传图稿 / 拍照' : '重新上传图稿') : '当前状态不可上传图稿' }}
        </el-button>
        <p v-if="!canUpload" class="up-tip">
          只有「{{ label('WAIT_DRAFT') }}」或「{{ label('REJECTED') }}」状态可以提交图稿。
        </p>
      </div>

      <!-- 审核记录 -->
      <div class="sent-card">
        <div class="list-head"><strong>审核记录</strong></div>
        <div v-if="logs.length === 0" class="review-empty">
          还没有审核记录，客服审核后会显示在这里
        </div>
        <div v-for="(r, i) in logs" :key="i" class="review-item">
          <div class="review-item__head">
            <el-tag size="small" :type="r.result === 'PASS' ? 'success' : 'danger'">
              {{ r.result === 'PASS' ? '审核通过' : '审核驳回' }}
            </el-tag>
            <span class="review-item__ver">图稿 v{{ r.draftVersion }}</span>
            <span class="review-item__time">{{ timeText(r.reviewedAtMillis) }}</span>
          </div>
          <p v-if="r.remark" class="review-item__remark">{{ r.remark }}</p>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 定制单详情页（`/custom/:customOrderId`）。
 *
 * 背景：「我的定制」列表原来点一行只在下方展开几个字段、没有独立页面，
 * 用户看不到自己传过的图稿、也看不清客服为什么驳回。这里做成真正的详情页：
 * 状态提示 + 基础信息（含关联订单）+ 图稿版本（可放大）+ 审核记录 + 上传/重传图稿。
 *
 * 数据一次拿全（后端详情已联查 custom_draft / custom_review_log），不再发多个请求。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { fmtDateTime, fmtMoney, statusLabel, statusTone, type StatusTone } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { customApi, type CustomVO } from '@/api/custom';
import { fileApi } from '@/api/file';

const route = useRoute();
const router = useRouter();

const customOrderId = computed(() => String(route.params.customOrderId ?? ''));
const detail = ref<CustomVO | null>(null);
const loading = ref(false);
const uploading = ref(false);
const fileInput = ref<HTMLInputElement | null>(null);

const drafts = computed(() => detail.value?.drafts ?? []);
const logs = computed(() => detail.value?.reviewLogs ?? []);
const previewList = computed(() => drafts.value.map((d) => fileApi.url(d.fileId)));

/** 只有「待提交图稿 / 审核驳回」能提交新稿（后端状态机同口径） */
const canUpload = computed(() => {
  const s = detail.value?.status;
  return s === 'WAIT_DRAFT' || s === 'REJECTED';
});

const STATUS_HINT: Record<string, { text: string; tone: 'info' | 'warn' | 'ok' }> = {
  WAIT_DRAFT: { text: '请先上传设计图稿，上传后客服会在 24 小时内完成审核。', tone: 'warn' },
  WAIT_REVIEW: { text: '图稿已提交，客服正在审核。审核结果会通过站内信通知你。', tone: 'info' },
  REJECTED: { text: '图稿被驳回，请按下方「驳回原因」修改后重新上传。', tone: 'warn' },
  APPROVED: { text: '图稿已通过审核，正在等待排产。', tone: 'ok' },
  IN_PRODUCTION: { text: '已排产，正在生产中。', tone: 'ok' },
  FINISHED: { text: '定制已完工，会随订单一起发货。', tone: 'ok' },
  SHIPPED: { text: '定制件已随订单发货，可在订单详情查看物流。', tone: 'ok' },
  CANCELLED: { text: '该定制单已取消。', tone: 'info' },
  ESCALATED: { text: '已提交主管处理，请耐心等待。', tone: 'warn' },
};

const hint = computed(
  () => STATUS_HINT[detail.value?.status ?? ''] ?? { text: '定制单处理中。', tone: 'info' as const },
);

const SIDE_LABELS: Record<string, string> = {
  SINGLE: '单面',
  DOUBLE: '双面',
  FULL: '全包',
};

/** 定制类型：随单附加 / 独立定制 */
const SERVICE_LABELS: Record<string, string> = {
  ATTACHED: '随单定制',
  STANDALONE: '独立定制',
};

function label(s: string): string {
  return statusLabel(s);
}
function tone(s: string): StatusTone {
  return statusTone(s);
}
function sideLabel(s?: string | null): string {
  return s ? (SIDE_LABELS[s] ?? s) : '—';
}
function serviceLabel(s?: string | null): string {
  return s ? (SERVICE_LABELS[s] ?? s) : '—';
}
/** 0 / 空值一律显示占位，避免出现 1970-01-01 */
function timeText(v?: number | string | null): string {
  const n = Number(v ?? 0);
  return n > 0 ? fmtDateTime(n) : '—';
}
function fileUrl(fileId: string): string {
  return fileApi.url(fileId);
}

function goOrder(): void {
  if (detail.value?.orderNo) router.push(`/order/${detail.value.orderNo}`);
}
function back(): void {
  router.push('/custom');
}

async function load(): Promise<void> {
  const id = customOrderId.value;
  if (!id) return;
  loading.value = true;
  try {
    detail.value = await unwrap(customApi.detail(id));
  } catch {
    detail.value = null;
    ElMessage.error('定制单加载失败');
  } finally {
    loading.value = false;
  }
}

function pickFile(): void {
  if (!canUpload.value) {
    ElMessage.warning('当前状态不可上传图稿');
    return;
  }
  fileInput.value?.click();
}

async function onPickFile(e: Event): Promise<void> {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file || !detail.value) return;

  uploading.value = true;
  try {
    // 先真传文件拿 fileId，再提交草稿：只传占位 fileId 时管理端审图台看不到图
    const stored = await unwrap(fileApi.upload(file));
    if (!stored?.fileId) throw new Error('图稿上传失败');
    await unwrap(
      customApi.uploadDraft(detail.value.customOrderId, {
        fileId: stored.fileId,
        fileName: stored.fileName || file.name,
        fileSize: Number(stored.size ?? file.size),
      }),
    );
    ElMessage.success('图稿已上传，等待客服审核');
    await load();
  } catch (err) {
    ElMessage.error((err as Error).message || '上传失败');
  } finally {
    uploading.value = false;
  }
}

onMounted(load);
watch(customOrderId, load);
</script>

<style scoped>
.custom-detail {
  padding-top: 14px;
}

/* ---------- 顶部 ---------- */
.custom-detail__bar {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: var(--sent-radius);
  padding: 12px 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.custom-detail__back {
  font-size: 13px;
  color: var(--sent-primary);
  cursor: pointer;
  flex: none;
}
.custom-detail__back:hover {
  color: var(--sent-accent);
}
.custom-detail__no {
  font-size: 17px;
  font-weight: 800;
  color: var(--sent-text);
  letter-spacing: 0.5px;
}
.custom-detail__status {
  margin-left: auto;
}

/* ---------- 状态提示 ---------- */
.detail-hint {
  border-radius: var(--sent-radius);
  padding: 11px 16px;
  font-size: 13px;
  margin-bottom: 12px;
  border-left: 4px solid transparent;
}
.detail-hint--warn {
  background: var(--sent-accent-soft);
  border-color: var(--sent-accent);
  color: var(--sent-accent-dark);
}
.detail-hint--info {
  background: var(--sent-primary-soft);
  border-color: var(--sent-primary);
  color: var(--sent-primary);
}
.detail-hint--ok {
  background: #eef8f2;
  border-color: #2f9e6a;
  color: #1e7a4f;
}

/* ---------- 基础信息 ---------- */
.custom-detail__info {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 32px;
}
.info-row {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--sent-text-2);
  padding: 8px 0;
  border-bottom: 1px dashed #f2f3f5;
}
.info-row > span:first-child {
  width: 76px;
  flex: none;
  color: var(--sent-muted);
}
.info-row > span:nth-child(2),
.info-row > b {
  flex: 1;
  min-width: 0;
  color: var(--sent-text-2);
  text-align: left;
}
.info-row b {
  color: var(--sent-text);
  font-size: 14px;
}
.info-link {
  color: var(--sent-primary);
  cursor: pointer;
}
.info-link:hover {
  color: var(--sent-accent);
}
.info-sub {
  font-style: normal;
  font-size: 12px;
  color: var(--sent-muted);
  margin-left: 6px;
}
.danger {
  color: #d4380d;
}

/* ---------- 图稿 ---------- */
.list-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding-bottom: 10px;
  font-size: 14px;
}
.list-head__sub {
  font-size: 12px;
  color: var(--sent-muted);
  font-weight: 400;
}
.draft-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 12px;
}
.draft-item {
  border: 1px solid #eef0f2;
  border-radius: 8px;
  overflow: hidden;
  background: #fafbfc;
}
.draft-item--current {
  border-color: var(--sent-primary);
  box-shadow: 0 0 0 2px var(--sent-primary-soft);
}
.draft-item__img {
  width: 100%;
  height: 130px;
  display: block;
  cursor: zoom-in;
}
.draft-item__meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 7px 10px;
  font-size: 12px;
  color: var(--sent-text-2);
}
.draft-item__meta b {
  color: var(--sent-text);
}
.draft-item__badge {
  font-size: 11px;
  color: #fff;
  background: var(--sent-primary);
  border-radius: 3px;
  padding: 0 5px;
  line-height: 16px;
}
.draft-item__time {
  width: 100%;
  margin-top: 1px;
  color: var(--sent-muted);
  font-size: 11px;
}
.draft-empty {
  padding: 22px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
  background: #fafbfc;
  border: 1px dashed #e4e7ec;
  border-radius: 8px;
}
.draft-input {
  display: none;
}
.up-btn {
  width: 100%;
  margin-top: 14px;
}
.up-tip {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--sent-muted);
  text-align: center;
}

/* ---------- 审核记录 ---------- */
.review-empty {
  padding: 18px 0;
  text-align: center;
  font-size: 13px;
  color: var(--sent-muted);
  background: #fafbfc;
  border-radius: 8px;
}
.review-item {
  border-top: 1px solid #f2f3f5;
  padding: 10px 0;
}
.review-item__head {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--sent-text-2);
}
.review-item__time {
  margin-left: auto;
  font-size: 12px;
  color: var(--sent-muted);
}
.review-item__remark {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--sent-text-2);
  background: #fafbfc;
  border-radius: 6px;
  padding: 8px 10px;
}

@media (max-width: 900px) {
  .custom-detail__info {
    grid-template-columns: 1fr;
  }
}
</style>
