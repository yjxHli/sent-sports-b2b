<template>
  <div>
    <h2 class="sent-page-title">定制审核</h2>
    <div class="sent-panel">
      <div class="sent-form-row">
        <el-select
          v-model="selectedId"
          filterable
          clearable
          placeholder="选择待审核定制单"
          style="width: 280px"
          @change="onSelect"
        >
          <el-option
            v-for="item in pending"
            :key="item.customOrderId"
            :label="`${item.customOrderId} · ${statusLabel(item.status)}`"
            :value="item.customOrderId"
          />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadDetail">加载详情</el-button>
        <el-button @click="router.push('/custom/list')">查看全部</el-button>
        <span class="sent-hint">审核 SLA 到期未处理将升级主管，超时后不可直接通过</span>
      </div>

      <template v-if="detail">
        <el-descriptions :column="4" border class="detail-desc">
          <el-descriptions-item label="定制单号">{{ detail.customOrderId }}</el-descriptions-item>
          <el-descriptions-item label="状态"><StatusTag :status="detail.status" /></el-descriptions-item>
          <el-descriptions-item label="图稿版本">v{{ detail.version ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="驳回次数">{{ detail.rejectCount ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="审核截止">{{ fmtDateTime(detail.reviewDeadlineMillis) }}</el-descriptions-item>
          <el-descriptions-item label="剩余时间">
            <span :class="{ overdue: expired }">{{ remainingText }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="图稿文件">{{ detail.fileId ?? '--' }}</el-descriptions-item>
          <el-descriptions-item label="锁定版本">{{ detail.lockedVersion ?? '--' }}</el-descriptions-item>
        </el-descriptions>

        <div class="ledger-block">
          <div class="ledger-head">
            <strong>图稿与审图轨迹（真库）</strong>
            <span class="sent-hint">
              图稿 {{ detail.draftCount ?? 0 }} 版 · 审图记录 {{ detail.reviewLogCount ?? 0 }} 条
            </span>
          </div>
          <div v-for="d in detail.drafts ?? []" :key="`draft-${d.version}`" class="ledger-row">
            <span>v{{ d.version }} · {{ d.fileName || '--' }}</span><span>{{ d.fileId || '--' }}</span>
          </div>
          <div v-for="(r, idx) in detail.reviews ?? []" :key="`review-${idx}`" class="ledger-row">
            <span>v{{ r.draftVersion }} · {{ r.result }}</span>
            <span>{{ r.reasonCode || '--' }} {{ r.remark || '' }}</span>
          </div>
          <el-empty
            v-if="!(detail.drafts ?? []).length && !(detail.reviews ?? []).length"
            description="尚无图稿与审图记录"
            :image-size="50"
          />
        </div>

        <el-row :gutter="16" class="review-body">
          <el-col :xs="24" :md="10">
            <div class="draft-preview">
              <el-empty
                description="图稿由文件服务提供访问 URL，后端返回 fileId 后在此渲染预览"
                :image-size="70"
              />
            </div>
          </el-col>
          <el-col :xs="24" :md="14">
            <h3 class="section-title">审核处理</h3>
            <el-alert
              v-if="expired && detail.status === CustomStatus.WAIT_REVIEW"
              title="审核已超时，该单应升级主管处理"
              type="warning"
              :closable="false"
              show-icon
              class="review-alert"
            />

            <el-form label-width="100px">
              <el-form-item label="驳回原因">
                <el-select v-model="rejectForm.reasonCode" placeholder="选择原因码" style="width: 100%">
                  <el-option
                    v-for="r in REJECT_REASONS"
                    :key="r.code"
                    :label="r.label"
                    :value="r.code"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="审核备注">
                <el-input
                  v-model="rejectForm.remark"
                  type="textarea"
                  :rows="3"
                  placeholder="给客户/生产端的说明"
                />
              </el-form-item>
            </el-form>

            <div class="sent-form-row review-actions">
              <el-button
                type="success"
                :disabled="!canReview"
                :loading="submitting"
                @click="pass"
              >
                审核通过
              </el-button>
              <el-button
                type="danger"
                :disabled="!canReview"
                :loading="submitting"
                @click="reject"
              >
                驳回重做
              </el-button>
              <el-button
                :disabled="detail.status !== CustomStatus.APPROVED"
                :loading="submitting"
                @click="production"
              >
                转生产
              </el-button>
              <el-button
                :disabled="detail.status !== CustomStatus.IN_PRODUCTION"
                :loading="submitting"
                @click="finish"
              >
                标记完工
              </el-button>
            </div>
            <p class="sent-hint">
              状态机：待审核 → 通过/驳回；通过 → 转生产；生产中 → 完工。按钮按当前状态自动禁用。
            </p>
          </el-col>
        </el-row>
      </template>
      <el-empty v-else description="请选择一笔定制单" :image-size="80" />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 定制审核台（P6-36）：图稿占位 + 审核 SLA 倒计时 + 按状态机收敛的操作按钮。
 * 审核通过/驳回/转生产/完工全部走 customApi，页面不直连 http，也不手写状态跳转判断以外的业务规则。
 */
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { unwrap } from '@/utils/http';
import { customApi } from '@/api';
import type { CustomVO } from '@sent/shared';
import { CustomStatus, fmtDateTime, statusLabel } from '@sent/shared';
import StatusTag from '@/components/StatusTag.vue';

const REJECT_REASONS = [
  { code: 'DRAFT_UNCLEAR', label: '图稿不清晰' },
  { code: 'COPYRIGHT', label: '素材涉嫌侵权' },
  { code: 'CRAFT_UNSUPPORTED', label: '工艺不可实现' },
  { code: 'PRICE_RECALC', label: '价格需重新核算' },
  { code: 'OTHER', label: '其他' },
] as const;

const route = useRoute();
const router = useRouter();

const pending = ref<CustomVO[]>([]);
const detail = ref<any>(null);
const selectedId = ref<string>('');
const loading = ref(false);
const submitting = ref(false);
const now = ref(Date.now());
const rejectForm = reactive<{ reasonCode: string; remark: string }>({
  reasonCode: 'DRAFT_UNCLEAR',
  remark: '',
});

let timer: ReturnType<typeof setInterval> | undefined;

const canReview = computed(() =>
  detail.value?.status === CustomStatus.WAIT_REVIEW && !expired.value,
);
const expired = computed(() => {
  const deadline = detail.value?.reviewDeadlineMillis;
  return typeof deadline === 'number' && deadline > 0 && now.value >= deadline;
});
const remainingText = computed(() => {
  const deadline = detail.value?.reviewDeadlineMillis;
  if (typeof deadline !== 'number' || deadline <= 0) return '未设置';
  const left = deadline - now.value;
  if (left <= 0) return '已超时';
  const totalSeconds = Math.floor(left / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return `${hours}h ${minutes}m ${seconds}s`;
});

async function loadPending(): Promise<void> {
  try {
    const data = await unwrap(customApi.pending({ page: 1, size: 100 }));
    pending.value = data?.list ?? [];
  } catch {
    pending.value = [];
  }
}

async function loadDetail(): Promise<void> {
  const id = selectedId.value || String(route.query.id ?? '');
  if (!id) {
    ElMessage.warning('请先选择定制单');
    return;
  }
  selectedId.value = id;
  loading.value = true;
  try {
    detail.value = await unwrap(customApi.detail(id));
  } catch (error) {
    detail.value = null;
    ElMessage.error((error as Error)?.message || '定制单详情加载失败');
  } finally {
    loading.value = false;
  }
}

function onSelect(): void {
  if (selectedId.value) void loadDetail();
  else detail.value = null;
}

async function run(action: () => Promise<unknown>, successText: string): Promise<void> {
  if (!detail.value) return;
  submitting.value = true;
  try {
    await action();
    ElMessage.success(successText);
    await loadDetail();
  } catch (error) {
    ElMessage.error((error as Error)?.message || '操作失败');
  } finally {
    submitting.value = false;
  }
}

function pass(): void {
  void run(() => unwrap(customApi.pass(String(detail.value.customOrderId))), '审核已通过');
}

async function reject(): Promise<void> {
  if (!rejectForm.remark.trim()) {
    ElMessage.warning('请填写审核备注');
    return;
  }
  await run(
    () => unwrap(customApi.reject(String(detail.value.customOrderId), {
      reasonCode: rejectForm.reasonCode,
      remark: rejectForm.remark.trim(),
    })),
    '已驳回，等待客户重交图稿',
  );
}

function production(): void {
  void run(() => unwrap(customApi.production(String(detail.value.customOrderId))), '已转生产');
}

function finish(): void {
  void run(() => unwrap(customApi.finish(String(detail.value.customOrderId))), '已标记完工');
}

onMounted(async () => {
  timer = setInterval(() => { now.value = Date.now(); }, 1000);
  await loadPending();
  const id = String(route.query.id ?? '');
  if (id) {
    selectedId.value = id;
    await loadDetail();
  }
});
onUnmounted(() => { if (timer) clearInterval(timer); });
</script>

<style scoped>
.detail-desc { margin-top: 14px; }
.ledger-block { margin-top: 16px; border-top: 1px dashed #dde6e1; padding-top: 12px; }
.ledger-head { display: flex; justify-content: space-between; align-items: center; font-size: 14px; }
.ledger-row {
  display: flex; justify-content: space-between; gap: 12px;
  font-size: 12px; color: #60706a; padding: 4px 0;
  border-bottom: 1px solid #f2f5f3;
}
.section-title { margin: 0 0 14px; font-size: 15px; color: #33463f; }
.review-body { margin-top: 18px; }
.review-alert { margin-bottom: 14px; }
.review-actions { margin-top: 8px; }
.draft-preview {
  border: 1px dashed #c8d6d0;
  border-radius: 6px;
  min-height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafcfa;
}
.overdue { color: #d03050; font-weight: 600; }
</style>
