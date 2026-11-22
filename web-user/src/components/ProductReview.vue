<template>
  <!-- 商品评价（V55）：详情页底部独立区块，锚点导航里叫「用户评价」 -->
  <section id="section-review" class="review" data-testid="review-list">
    <!-- 头部：好评率 / 评价总数 / 平均星级 + 「去评价」入口 -->
    <div class="review__head">
      <div class="review__headline">
        <span class="review__title">用户评价</span>
        <span class="review__count" data-testid="review-total">{{ total }} 条</span>
      </div>

      <div class="review__stats">
        <div class="review__rate">
          <span class="review__rate-num" data-testid="review-avg-rating">{{ summary.avgRating }}</span>
          <el-rate :model-value="avgStars" disabled allow-half size="small" />
        </div>
        <div class="review__good">
          好评率 <b data-testid="review-positive-rate">{{ summary.positiveRate }}%</b>
        </div>
      </div>

      <button
        v-if="eligibility.eligible"
        type="button"
        class="review__cta"
        data-testid="review-submit-btn"
        @click="openDialog"
      >
        去评价
      </button>
      <span v-else-if="eligibility.reviewed" class="review__done">您已评价</span>
    </div>

    <!-- 列表：极简卡片，头像用昵称首字 -->
    <div v-loading="loading" class="review__body">
      <el-empty
        v-if="!loading && !list.length"
        description="还没有评价，购买后来做第一个评价的人吧"
        :image-size="72"
      />

      <article
        v-for="item in list"
        :key="item.reviewId"
        class="review-card"
        data-testid="review-item"
      >
        <div class="review-card__side">
          <span class="review-card__avatar" data-testid="review-avatar">{{ item.avatarText }}</span>
          <span class="review-card__name">{{ item.nickname }}</span>
        </div>

        <div class="review-card__main">
          <div class="review-card__meta">
            <el-rate :model-value="item.rating" disabled size="small" />
            <span class="review-card__time">{{ fmtTime(item.createdAt) }}</span>
          </div>

          <p class="review-card__text">{{ item.content }}</p>

          <!-- 九宫格：最多 3 张，点击可放大预览（preview-src-list） -->
          <div v-if="item.images.length" class="review-card__imgs">
            <el-image
              v-for="(img, i) in item.images"
              :key="item.reviewId + '-img-' + i"
              class="review-card__img"
              :src="fileApi.url(img)"
              :preview-src-list="item.images.map((f) => fileApi.url(f))"
              :initial-index="i"
              fit="cover"
              preview-teleported
              data-testid="review-img"
            />
          </div>
        </div>
      </article>

      <div v-if="hasMore" class="review__more">
        <button type="button" class="review__more-btn" :disabled="loading" @click="loadMore">
          {{ loading ? '加载中…' : '查看更多评价' }}
        </button>
      </div>
    </div>

    <!-- 发布评价 -->
    <el-dialog
      v-model="dialogVisible"
      title="评价这件商品"
      width="520px"
      align-center
      destroy-on-close
      data-testid="review-dialog"
    >
      <div class="rv-form">
        <div class="rv-form__row">
          <span class="rv-form__label">评分</span>
          <el-rate v-model="rating" show-text :texts="RATE_TEXTS" data-testid="review-dialog-rate" />
        </div>

        <div class="rv-form__row rv-form__row--block">
          <span class="rv-form__label">评价内容</span>
          <el-input
            v-model="content"
            type="textarea"
            :rows="4"
            maxlength="500"
            show-word-limit
            placeholder="说说用料、手感、尺码或物流体验，给同事一个参考"
            data-testid="review-dialog-content"
          />
        </div>

        <div class="rv-form__row rv-form__row--block">
          <span class="rv-form__label">晒图（最多 {{ MAX_IMAGES }} 张）</span>
          <el-upload
            v-model:file-list="files"
            class="rv-form__upload"
            list-type="picture-card"
            accept="image/*"
            :auto-upload="false"
            :limit="MAX_IMAGES"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
            :on-exceed="onExceed"
            data-testid="review-upload-img"
          >
            <span class="rv-form__upload-add">＋</span>
          </el-upload>
        </div>

        <div class="rv-form__row">
          <el-checkbox v-model="anonymous" data-testid="review-dialog-anonymous">匿名评价</el-checkbox>
          <span class="rv-form__hint">匿名后对外只显示「匿名用户」</span>
        </div>
      </div>

      <template #footer>
        <button type="button" class="rv-btn rv-btn--ghost" :disabled="submitting" @click="dialogVisible = false">
          取消
        </button>
        <button
          type="button"
          class="rv-btn rv-btn--primary"
          :disabled="submitting"
          data-testid="review-dialog-submit"
          @click="submit"
        >
          {{ submitting ? '提交中…' : '提交评价' }}
        </button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
/**
 * 商品评价区块（V55）。
 *
 * 为什么独立成组件而不是塞进 detail.vue：详情页已有 1300 行（图集/秒杀/定制上传），
 * 评价是「读列表 + 写表单」的另一套状态，混在一起会让秒杀倒计时与评价分页互相牵连。
 *
 * 关键口径：
 * - 后端 Long 一律序列化为字符串，计数字段全部 `Number()`，否则 `1 < "10"` 这类比较会错；
 * - 图片先传文件服务拿 fileId 再随评价提交（二进制不进 JSON）；
 * - 「去评价」显隐由 `/review-eligibility` 决定，不靠提交失败反推。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import type { UploadFile, UploadUserFile } from 'element-plus';
import { fmtDateTime } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { reviewApi, type ReviewSummaryVO, type ReviewVO } from '@/api/review';
import { fileApi } from '@/api/file';

const props = defineProps<{
  productId: string | number;
  /** 当前选中规格：评价归属到具体 SKU（可空 → 商品级评价） */
  skuId?: string | number | null;
}>();

const PAGE_SIZE = 5;
const MAX_IMAGES = 3;
const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const RATE_TEXTS = ['很差', '较差', '一般', '满意', '非常满意'];

const loading = ref(false);
const list = ref<ReviewVO[]>([]);
const total = ref(0);
const page = ref(1);
const summary = ref<ReviewSummaryVO>({ total: 0, positiveCount: 0, positiveRate: 0, avgRating: '0.0' });
const eligibility = ref({ eligible: false, reviewed: false });

const dialogVisible = ref(false);
const submitting = ref(false);
const rating = ref(5);
const content = ref('');
const anonymous = ref(false);
const files = ref<UploadUserFile[]>([]);
/** file.uid → 已上传的 fileId，避免提交时同一张图重复上传 */
const uploadedIds = new Map<string, string>();

const avgStars = computed(() => Number(summary.value.avgRating) || 0);
const hasMore = computed(() => list.value.length < total.value);

function fmtTime(value: number | string): string {
  const ms = Number(value);
  return Number.isFinite(ms) && ms > 0 ? fmtDateTime(ms) : '';
}

async function load(reset = false): Promise<void> {
  const pid = Number(props.productId);
  if (!Number.isFinite(pid) || pid <= 0) return;
  loading.value = true;
  try {
    const target = reset ? 1 : page.value;
    const res = await unwrap(reviewApi.list(pid, { page: target, size: PAGE_SIZE }));
    const rows = (res?.list ?? []).map((item) => ({
      ...item,
      images: Array.isArray(item.images) ? item.images.filter((f) => !!f) : [],
    }));
    list.value = reset ? rows : [...list.value, ...rows];
    total.value = Number(res?.total ?? 0);
    page.value = target;
    if (res?.summary) {
      summary.value = {
        total: Number(res.summary.total ?? 0),
        positiveCount: Number(res.summary.positiveCount ?? 0),
        positiveRate: Number(res.summary.positiveRate ?? 0),
        avgRating: String(res.summary.avgRating ?? '0.0'),
      };
    }
  } catch {
    /* 评价区加载失败不该让详情页报错：保持空态即可 */
  } finally {
    loading.value = false;
  }
}

async function loadMore(): Promise<void> {
  page.value += 1;
  await load(false);
}

async function loadEligibility(): Promise<void> {
  const pid = Number(props.productId);
  if (!Number.isFinite(pid) || pid <= 0) return;
  try {
    const res = await unwrap(reviewApi.eligibility(pid));
    eligibility.value = { eligible: Boolean(res?.eligible), reviewed: Boolean(res?.reviewed) };
  } catch {
    eligibility.value = { eligible: false, reviewed: false };
  }
}

function openDialog(): void {
  resetForm();
  dialogVisible.value = true;
}

function resetForm(): void {
  rating.value = 5;
  content.value = '';
  anonymous.value = false;
  files.value = [];
  uploadedIds.clear();
}

function onFileChange(file: UploadFile): void {
  if (file.raw && file.raw.size > MAX_IMAGE_BYTES) {
    ElMessage.error('单张图片不超过 10MB');
    files.value = files.value.filter((f) => f.uid !== file.uid);
    return;
  }
  // 本地预览：picture-card 没给 url 时缩略图是空白，用户会以为没选上
  if (file.raw && !file.url) {
    file.url = URL.createObjectURL(file.raw);
  }
}

function onFileRemove(file: UploadFile): void {
  uploadedIds.delete(String(file.uid));
  if (file.url?.startsWith('blob:')) URL.revokeObjectURL(file.url);
}

function onExceed(): void {
  ElMessage.warning(`最多上传 ${MAX_IMAGES} 张图片`);
}

/** 逐张上传拿 fileId；已传过的直接复用，失败即中断（不允许提交半套图） */
async function uploadImages(): Promise<string[]> {
  const ids: string[] = [];
  for (const file of files.value) {
    const cached = uploadedIds.get(String(file.uid));
    if (cached) {
      ids.push(cached);
      continue;
    }
    if (!file.raw) continue;
    const uploaded = await unwrap(fileApi.upload(file.raw));
    if (!uploaded?.fileId) throw new Error('图片上传失败，请重试');
    uploadedIds.set(String(file.uid), uploaded.fileId);
    ids.push(uploaded.fileId);
  }
  return ids;
}

async function submit(): Promise<void> {
  const pid = Number(props.productId);
  const text = content.value.trim();
  if (!pid || pid <= 0) return;
  if (!rating.value) {
    ElMessage.warning('请先给个评分');
    return;
  }
  if (!text) {
    ElMessage.warning('请填写评价内容');
    return;
  }
  submitting.value = true;
  try {
    const images = await uploadImages();
    const skuId = props.skuId == null || props.skuId === '' ? null : Number(props.skuId);
    await unwrap(reviewApi.submit({
      productId: pid,
      skuId: skuId != null && Number.isFinite(skuId) ? skuId : null,
      rating: rating.value,
      content: text,
      images,
      anonymous: anonymous.value,
    }));
    ElMessage.success('评价已提交，感谢分享');
    dialogVisible.value = false;
    resetForm();
    await load(true);
    await loadEligibility();
  } catch (e) {
    ElMessage.error((e as Error).message || '评价提交失败');
  } finally {
    submitting.value = false;
  }
}

watch(() => props.productId, () => {
  list.value = [];
  total.value = 0;
  page.value = 1;
  void load(true);
  void loadEligibility();
});

onMounted(() => {
  void load(true);
  void loadEligibility();
});
</script>

<style scoped>
/* 去 AI 化：不用渐变/发光/彩色描边，只有极淡阴影与 1px 分隔线 */
.review {
  margin-top: 16px;
  padding: 20px 24px 8px;
  background: var(--sent-card, #fff);
  border-radius: var(--sent-radius, 8px);
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.review__head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--sent-border, #ececec);
}

.review__headline {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.review__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--sent-ink, var(--sent-text, #1f2329));
}

.review__count {
  font-size: 12px;
  color: var(--sent-muted, #8b93a1);
}

.review__stats {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-left: 8px;
}

.review__rate {
  display: flex;
  align-items: center;
  gap: 6px;
}

.review__rate-num {
  font-size: 20px;
  font-weight: 600;
  color: var(--sent-accent, #ff7a1a);
  font-variant-numeric: tabular-nums;
}

.review__good {
  font-size: 13px;
  color: var(--sent-text-2, #4b5563);
}

.review__good b {
  color: var(--sent-primary, #12664f);
  font-variant-numeric: tabular-nums;
}

.review__cta {
  margin-left: auto;
  height: 32px;
  padding: 0 16px;
  border: 1px solid var(--sent-primary, #12664f);
  border-radius: var(--sent-radius, 8px);
  background: var(--sent-card, #fff);
  color: var(--sent-primary, #12664f);
  font-size: 13px;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.review__cta:hover {
  background: var(--sent-primary-soft, #e7f1ec);
}

.review__done {
  margin-left: auto;
  font-size: 12px;
  color: var(--sent-muted, #8b93a1);
}

.review__body {
  min-height: 80px;
}

.review-card {
  display: flex;
  gap: 14px;
  padding: 16px 0;
  border-bottom: 1px solid var(--sent-border, #ececec);
}

.review-card:last-of-type {
  border-bottom: none;
}

.review-card__side {
  width: 72px;
  flex: 0 0 72px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

/* 首字头像：正方形 + 主色浅底，不加载外部头像，避免匿名评价泄露真实头像 */
.review-card__avatar {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--sent-primary-soft, #e7f1ec);
  color: var(--sent-primary, #12664f);
  font-size: 15px;
  font-weight: 600;
}

.review-card__name {
  font-size: 12px;
  color: var(--sent-muted, #8b93a1);
  max-width: 72px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-card__main {
  flex: 1;
  min-width: 0;
}

.review-card__meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.review-card__time {
  font-size: 12px;
  color: var(--sent-muted, #8b93a1);
}

.review-card__text {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--sent-text, #1f2329);
  white-space: pre-wrap;
  word-break: break-word;
}

/* 九宫格：3 列定宽，正方形裁切（object-fit: cover 由 el-image 的 img 承接） */
.review-card__imgs {
  display: grid;
  grid-template-columns: repeat(3, 88px);
  gap: 8px;
  margin-top: 10px;
}

.review-card__img {
  width: 88px;
  height: 88px;
  border-radius: var(--sent-radius, 8px);
  overflow: hidden;
  background: var(--sent-bg, #f5f5f5);
  cursor: zoom-in;
}

.review-card__img :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.review__more {
  padding: 12px 0 16px;
  text-align: center;
}

.review__more-btn {
  height: 32px;
  padding: 0 18px;
  border: 1px solid var(--sent-border, #ececec);
  border-radius: var(--sent-radius, 8px);
  background: var(--sent-bg, #f5f5f5);
  color: var(--sent-text-2, #4b5563);
  font-size: 13px;
  cursor: pointer;
}

.review__more-btn:hover:not(:disabled) {
  color: var(--sent-primary, #12664f);
  border-color: var(--sent-primary, #12664f);
}

/* ---------------- 弹窗 ---------------- */
.rv-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.rv-form__row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rv-form__row--block {
  align-items: flex-start;
  flex-direction: column;
  gap: 8px;
}

.rv-form__label {
  font-size: 13px;
  color: var(--sent-text-2, #4b5563);
}

.rv-form__hint {
  font-size: 12px;
  color: var(--sent-muted, #8b93a1);
}

.rv-form__upload :deep(.el-upload--picture-card),
.rv-form__upload :deep(.el-upload-list__item) {
  width: 72px;
  height: 72px;
  border-radius: var(--sent-radius, 8px);
}

.rv-form__upload-add {
  font-size: 18px;
  color: var(--sent-muted, #8b93a1);
  line-height: 1;
}

.rv-btn {
  height: 32px;
  padding: 0 16px;
  border-radius: var(--sent-radius, 8px);
  font-size: 13px;
  cursor: pointer;
  border: 1px solid transparent;
}

.rv-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.rv-btn--ghost {
  background: var(--sent-card, #fff);
  border-color: var(--sent-border, #ececec);
  color: var(--sent-text-2, #4b5563);
}

.rv-btn--primary {
  margin-left: 8px;
  background: var(--sent-primary, #12664f);
  color: #fff;
}

.rv-btn--primary:hover:not(:disabled) {
  background: var(--sent-primary-dark, #0d4d3b);
}
</style>
