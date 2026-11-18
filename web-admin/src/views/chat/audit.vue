<template>
  <div>
    <h2 class="sent-page-title">客服审查</h2>
    <p class="sent-hint audit-intro">
      仅超级管理员与客服经理可见。查看每位客服与客户的完整沟通记录与本次服务小结。
    </p>

    <div class="sent-panel">
      <SentTable
        :columns="columns"
        :rows="rows"
        :total="total"
        :page="page"
        :size="size"
        :loading="loading"
        :page-sizes="tableSizes"
        :selectable="deleteMode"
        row-key="sessionId"
        :operation-width="130"
        @selection-change="selected = $event"
        @update:page="onPage"
        @update:size="onSize"
        @refresh="load"
      >
        <template #toolbar-actions>
          <BatchDeleteButton
            v-model:active="deleteMode"
            :ids="selected.map((r: ChatAuditRowVO) => String(r.sessionId))"
            :summary="`已选择 ${selected.length} 条审查记录：${selected.map((r: ChatAuditRowVO) => r.sessionId).join('、')}`"
            :delete-fn="(ids, config) => unwrap(chatApi.batchDeleteAuditSessions(ids, config))"
            @done="onDeleted"
          />
        </template>
        <template #toolbar>
          <el-select v-model="filter.staffId" clearable placeholder="全部客服" style="width: 150px">
            <el-option v-for="s in staffOptions" :key="s.staffId" :label="s.name" :value="s.staffId" />
          </el-select>
          <el-select v-model="filter.result" clearable placeholder="全部处理结果" style="width: 140px">
            <el-option label="已完成" value="DONE" />
            <el-option label="未解决" value="UNRESOLVED" />
          </el-select>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px"
          />
          <el-input v-model="filter.keyword" clearable placeholder="用户 ID / 会话号 / 问题概述" style="width: 220px"
                    @keyup.enter="onSearch" />
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
          <span class="sent-hint">共 {{ total }} 条</span>
        </template>

        <template #col-customerId="{ row }"><span data-testid="audit-customer-id">{{ row.customerId }}</span></template>
        <template #col-staffId="{ row }">
          <template v-if="row.staffId">{{ row.staffId }} {{ row.staffName || '' }}</template>
          <span v-else>--</span>
        </template>
        <template #col-startedAtMillis="{ row }">{{ fmtDateTime(Number(row.startedAtMillis ?? 0)) }}</template>
        <template #col-result="{ row }">
          <StatusTag :label="resultLabel(row.result)" :tone="row.result === 'DONE' ? 'success' : 'warning'" />
        </template>
        <template #col-noteStatus="{ row }">
          <StatusTag :label="row.noteStatus" :tone="row.noteStatus === '已填写' ? 'success' : 'info'" />
        </template>
        <template #operations="{ row }">
          <el-button link type="primary" data-testid="audit-detail-btn" @click="openDetail(row.sessionId)">
            详情
          </el-button>
        </template>
      </SentTable>
    </div>

    <!-- 详情：左侧聊天回放，右侧客服填写的具体会话信息 -->
    <el-dialog
      v-model="detailVisible"
      width="1120px"
      top="4vh"
      class="audit-dialog"
      append-to-body
      title="会话详情"
    >
      <div v-if="detailLoading" class="sent-hint">加载中…</div>
      <template v-else-if="transcript">
        <el-descriptions :column="2" border size="small" class="audit-meta">
          <el-descriptions-item label="会话号">{{ transcript.session.sessionId }}</el-descriptions-item>
          <el-descriptions-item label="用户 ID">{{ transcript.session.customerId }}</el-descriptions-item>
          <el-descriptions-item label="客服 ID">{{ transcript.session.staffId ?? '--' }}</el-descriptions-item>
          <el-descriptions-item label="聊天时间">
            {{ fmtDateTime(Number(transcript.session.startedAtMillis ?? 0)) }} ~
            {{ fmtDateTime(Number(transcript.session.endedAtMillis ?? 0)) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="audit-detail-grid">
          <section class="audit-transcript" data-testid="audit-transcript">
            <h4>聊天记录</h4>
            <div class="audit-transcript__body">
              <!-- 与工作台历史回放同一口径：5 分钟时间分隔 + 左用户 / 右客服气泡，固定高度可滚动 -->
              <template v-for="(m, mi) in transcript.messages" :key="m.seq">
                <div
                  v-if="shouldShowTimeDivider(
                    transcript.messages[mi - 1]?.createdAtMillis ?? 0,
                    Number(m.createdAtMillis ?? 0))"
                  class="audit-msg-time"
                >{{ fmtChatTime(Number(m.createdAtMillis ?? 0)) }}</div>
                <div class="audit-msg-line" :class="{ mine: m.senderType === 'STAFF' }">
                  <span v-if="m.senderType === 'SYSTEM'" class="audit-system-card">{{ systemMessageText(m) }}</span>
                  <span v-else class="audit-bubble">{{ m.content }}</span>
                </div>
              </template>
              <p v-if="!transcript.messages.length" class="sent-hint audit-msg-empty">该会话没有消息</p>
            </div>
          </section>

          <section class="audit-note" data-testid="audit-note">
            <h4>客服填写信息</h4>
            <template v-if="transcript.note">
              <el-descriptions :column="1" border size="small">
                <el-descriptions-item label="问题概述">{{ transcript.note.summary }}</el-descriptions-item>
                <el-descriptions-item label="问题分类">{{ categoryLabel(transcript.note.category) }}</el-descriptions-item>
                <el-descriptions-item label="用户问题">{{ infoLabel(transcript.note.info) }}</el-descriptions-item>
                <el-descriptions-item label="处理结果">{{ resultLabel(transcript.note.result) }}</el-descriptions-item>
                <el-descriptions-item label="解决办法">{{ transcript.note.solution }}</el-descriptions-item>
              </el-descriptions>
            </template>
            <p v-else class="sent-hint">该会话未填写小结</p>
          </section>
        </div>
      </template>
      <div v-else class="sent-hint">会话详情加载失败</div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 客服审查（2026-11-18 用户要求）：客服经理 / 超管按用户 ID、客服 ID、聊天时间、
 * 问题概述、解决情况检索会话；详情同时展示聊天回放与客服填写的小结。
 * 后端以 @RequirePerm(exact=true) 精确校验，持 '*' 的其他业务角色不能越权访问接口。
 */
import { onMounted, reactive, ref } from 'vue';
import { fmtChatTime, fmtDateTime, shouldShowTimeDivider } from '@sent/shared';
import { unwrap } from '@/utils/http';
import { chatApi, type ChatAuditRowVO, type ChatAuditStaffVO, type ChatTranscriptVO } from '@/api';
import { usePagination } from '@/composables/usePagination';
import SentTable from '@/components/SentTable.vue';
import StatusTag from '@/components/StatusTag.vue';
import BatchDeleteButton from '@/components/BatchDeleteButton.vue';
import type { TableColumn } from '@/components/types';

const columns: TableColumn[] = [
  { prop: 'sessionId', label: '会话号', width: 110 },
  { prop: 'customerId', label: '用户 ID', width: 110 },
  { prop: 'staffId', label: '客服 ID', width: 160 },
  { prop: 'startedAtMillis', label: '聊天时间', minWidth: 180 },
  { prop: 'summary', label: '问题概述', minWidth: 180 },
  { prop: 'result', label: '解决情况', width: 110, align: 'center' },
  { prop: 'noteStatus', label: '小结', width: 100, align: 'center' },
];

const { page, size } = usePagination();
const tableSizes = [10, 15, 20, 50, 100];
const rows = ref<ChatAuditRowVO[]>([]);
const total = ref(0);
const loading = ref(false);
/** 批量删除模式与勾选集合（与发票管理同一套 BatchDeleteButton 口径） */
const deleteMode = ref(false);
const selected = ref<ChatAuditRowVO[]>([]);

function onDeleted(): void {
  selected.value = [];
  void load();
}
const staffOptions = ref<ChatAuditStaffVO[]>([]);
const filter = reactive({
  staffId: undefined as number | undefined,
  result: '',
  keyword: '',
});
const dateRange = ref<string[] | null>(null);

function resultLabel(value?: string | null): string {
  if (value === 'DONE') return '已完成';
  if (value === 'UNRESOLVED') return '未解决';
  return '未填写';
}

function categoryLabel(value?: string | null): string {
  const labels: Record<string, string> = {
    QUALITY: '商品品质问题', INQUIRY: '商品咨询', UNCLEAR: '未明确',
  };
  return labels[value ?? ''] ?? (value ?? '--');
}

function infoLabel(value?: string | null): string {
  const labels: Record<string, string> = { CLEAR: '明确信息', NO_REPLY: '用户未回复' };
  return labels[value ?? ''] ?? (value ?? '--');
}

/**
 * 系统消息里的满意度评价卡 content 是裸 JSON（{"status":"PENDING"}），
 * 审查回放不展示这种技术报文，统一改成可读的「会话已结束」。
 */
function systemMessageText(m: { senderType: string; msgType: string; content: string }): string {
  const raw = String(m.content ?? '').trim();
  if (m.msgType === 'SATISFY' || raw.startsWith('{')) return '会话已结束';
  return raw;
}

async function load(): Promise<void> {
  loading.value = true;
  try {
    const data = await unwrap(chatApi.auditSessions({
      page: page.value,
      size: size.value,
      staffId: filter.staffId,
      result: filter.result || undefined,
      keyword: filter.keyword || undefined,
      from: dateRange.value?.[0],
      to: dateRange.value?.[1],
    }));
    // JacksonConfig 把 Long 序列化成字符串，列表行统一 Number() 归一
    rows.value = (data?.list ?? []).map((r) => ({
      ...r,
      sessionId: Number(r.sessionId),
      customerId: Number(r.customerId),
      staffId: Number(r.staffId),
      startedAtMillis: Number(r.startedAtMillis ?? 0),
      endedAtMillis: Number(r.endedAtMillis ?? 0),
    }));
    total.value = Number(data?.total ?? 0);
  } catch {
    rows.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function onPage(value: number): void {
  page.value = value;
  void load();
}

function onSize(value: number): void {
  size.value = value;
  page.value = 1;
  void load();
}

function onSearch(): void {
  page.value = 1;
  void load();
}

function resetFilters(): void {
  filter.staffId = undefined;
  filter.result = '';
  filter.keyword = '';
  dateRange.value = null;
  onSearch();
}

const detailVisible = ref(false);
const detailLoading = ref(false);
const transcript = ref<ChatTranscriptVO | null>(null);

async function openDetail(sessionId: number): Promise<void> {
  detailVisible.value = true;
  detailLoading.value = true;
  transcript.value = null;
  try {
    transcript.value = await unwrap(chatApi.transcript(sessionId));
  } catch {
    transcript.value = null;
  } finally {
    detailLoading.value = false;
  }
}

onMounted(async () => {
  try {
    staffOptions.value = (await unwrap(chatApi.auditStaff())) ?? [];
  } catch {
    staffOptions.value = [];
  }
  await load();
});
</script>

<style scoped>
.audit-intro { margin-bottom: 12px; }
.audit-meta { margin-bottom: 14px; }
.audit-detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(0, 0.75fr);
  gap: 16px;
}
.audit-detail-grid h4 { margin: 0 0 10px; font-size: 14px; color: #1f2d28; }
.audit-transcript {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border: 1px solid #e6ece9;
  border-radius: 8px;
  padding: 12px;
  background: #f7faf8;
}
/* 聊天回放：固定高度 + 内部滚动，不再随消息条数把整个弹窗撑爆 */
.audit-transcript__body {
  flex: 1;
  min-height: 300px;
  max-height: 62vh;
  overflow-y: auto;
  padding: 8px 14px 14px;
  border-radius: 8px;
}
.audit-msg-time { text-align: center; margin: 10px 0 6px; font-size: 11px; color: #9aa4b2; }
.audit-msg-line { display: flex; margin-bottom: 10px; }
.audit-msg-line.mine { justify-content: flex-end; }
.audit-bubble {
  max-width: 76%;
  padding: 9px 13px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #e3ebe7;
  box-shadow: 0 1px 2px rgba(16, 40, 32, 0.04);
  font-size: 13px;
  line-height: 1.5;
  word-break: break-word;
}
.audit-msg-line.mine .audit-bubble { background: #12664f; color: #fff; border-color: #12664f; }
.audit-system-card {
  max-width: 76%;
  padding: 8px 12px;
  border-radius: 10px;
  background: #eef2f0;
  color: #6b7a74;
  font-size: 12px;
  word-break: break-word;
}
.audit-msg-empty { text-align: center; padding-top: 80px; }
@media (max-width: 900px) {
  .audit-detail-grid { grid-template-columns: 1fr; }
}
</style>
