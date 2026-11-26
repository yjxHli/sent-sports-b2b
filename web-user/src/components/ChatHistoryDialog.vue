<template>
  <ChatModal
    :open="open"
    testid="chat-history-dialog"
    panel-class="history-dlg"
    width="720px"
    @close="close"
  >
    <header class="history-dlg__head">
      <div class="history-dlg__title">
        <b>历史咨询记录</b>
        <span>点击左侧任一次咨询，查看当时和客服的完整对话</span>
      </div>
      <button type="button" class="history-dlg__close" title="关闭" @click="close">×</button>
    </header>

    <div class="history-dlg__body">
      <aside class="history-dlg__list">
        <div v-if="loading" class="history-dlg__state">加载中…</div>
        <div v-else-if="sessions.length === 0" class="history-dlg__state">
          还没有历史咨询，直接问客服就好
        </div>
        <template v-else>
          <button
            v-for="s in sessions"
            :key="s.sessionId"
            type="button"
            class="history-dlg__row"
            data-testid="history-session-row"
            :class="{ 'is-active': activeId === Number(s.sessionId) }"
            @click="pick(s)"
          >
            <span class="history-dlg__row-top">
              <b>会话 #{{ Number(s.sessionId) }}</b>
              <i class="history-dlg__tag" :class="`history-dlg__tag--${statusKey(s.status)}`">
                {{ statusText(s.status) }}
              </i>
            </span>
            <span class="history-dlg__row-time">{{ fmtDateTime(Number(s.lastActiveAtMillis ?? s.createdAtMillis)) }}</span>
            <span class="history-dlg__row-staff">{{ staffText(s) }}</span>
          </button>
        </template>
      </aside>

      <section class="history-dlg__messages">
        <div v-if="msgLoading" class="history-dlg__state">正在读取对话…</div>
        <div v-else-if="!activeId" class="history-dlg__state">选择左侧一次咨询</div>
        <div v-else-if="msgs.length === 0" class="history-dlg__state">这次咨询没有留言记录</div>
        <div v-else class="history-dlg__msg-list">
          <template v-for="m in msgs" :key="m.messageId">
            <!-- 服务评价卡在消息表里是 JSON，历史回看要翻成人话，别把裸 JSON 摆给客户 -->
            <div v-if="m.msgType === 'SATISFY'" class="history-dlg__system">
              本次服务评价：{{ satisfyText(m.content) }}
            </div>
            <div v-else-if="m.senderType === 'SYSTEM'" class="history-dlg__system">{{ m.content }}</div>
            <div
              v-else
              class="history-dlg__msg"
              :class="m.senderType === 'CUSTOMER' ? 'history-dlg__msg--self' : 'history-dlg__msg--other'"
            >
              <span class="history-dlg__msg-time">{{ fmtChatTime(Number(m.createdAtMillis)) }}</span>
              <ChatOrderCard v-if="m.msgType === 'ORDER'" :content="m.content" :self="m.senderType === 'CUSTOMER'" />
              <img
                v-else-if="m.msgType === 'IMAGE'"
                class="history-dlg__msg-img"
                :src="fileApi.url(m.content)"
                alt="历史图片消息"
              />
              <span v-else class="history-dlg__bubble">{{ m.content }}</span>
            </div>
          </template>
        </div>
      </section>
    </div>
  </ChatModal>
</template>

<script setup lang="ts">
/**
 * 历史咨询记录弹窗（2026-11-24）。
 *
 * 数据分两步取，别一次拉全量：
 *   1) 打开弹窗先要会话摘要列表（GET /api/chat/sessions，只出 id/状态/时间）；
 *   2) 点某一条才去拉它的消息正文（GET /api/chat/sessions/{id}/messages）。
 * 演示库里单个客户有 2700+ 条历史会话，合在一起拉会把接口拖死。
 */
import { ref, watch } from 'vue';
import { SATISFY_LABELS, fmtChatTime, fmtDateTime } from '@sent/shared';
import { chatApi, type MessageVO, type SessionSummaryVO } from '@/api/chat';
import { fileApi } from '@/api/file';
import { unwrap } from '@/utils/http';
import ChatModal from './ChatModal.vue';
import ChatOrderCard from './ChatOrderCard.vue';

const props = withDefaults(defineProps<{
  open: boolean;
  sessions: SessionSummaryVO[];
  loading?: boolean;
}>(), { loading: false });

const emit = defineEmits<{ close: []; load: [] }>();

const activeId = ref<number | null>(null);
const msgs = ref<MessageVO[]>([]);
const msgLoading = ref(false);

watch(() => props.open, (open) => {
  if (!open) return;
  activeId.value = null;
  msgs.value = [];
  emit('load');
  // 会话列表可能在上次打开时已经缓存：有就直接选中第一条，没缓存就等 sessions 到达
  const cached = props.sessions[0];
  if (cached) void pick(cached);
});

// 列表异步到达后补选第一条：上面那次 pick 用的还是空列表
watch(() => props.sessions, (list) => {
  if (!props.open || activeId.value) return;
  if (list.length > 0) void pick(list[0]);
});

async function pick(s: SessionSummaryVO): Promise<void> {
  const id = Number(s.sessionId);
  activeId.value = id;
  msgLoading.value = true;
  msgs.value = [];
  try {
    msgs.value = await unwrap(chatApi.history(id, 0));
  } catch {
    msgs.value = [];
  } finally {
    msgLoading.value = false;
  }
}

function statusKey(status: string): string {
  if (status === 'ACTIVE') return 'active';
  if (status === 'WAITING') return 'waiting';
  return 'closed';
}

/** 评价卡 JSON → 「满意 / 一般 / 不满意」；没评过就明说未评价 */
function satisfyText(content: string): string {
  try {
    const parsed = JSON.parse(content || '{}');
    if (String(parsed.status ?? '') !== 'RATED') return '未评价';
    return SATISFY_LABELS[String(parsed.score ?? '')] ?? '已评价';
  } catch {
    return '已评价';
  }
}

function statusText(status: string): string {
  if (status === 'ACTIVE') return '服务中';
  if (status === 'WAITING') return '排队中';
  return '已结束';
}

/** 接待客服：老会话可能没落 staff_id（演示数据/E2E 建的），别显示成「待接入」误导客户 */
function staffText(s: SessionSummaryVO): string {
  if (s.staffId) return `客服 #${Number(s.staffId)}`;
  return s.status === 'WAITING' ? '待接入' : '人工客服';
}

function close(): void {
  emit('close');
}

defineExpose({ pick });
</script>

<style scoped>
.history-dlg__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 18px 12px;
  border-bottom: 1px solid #f2f4f5;
}
.history-dlg__title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.history-dlg__title b {
  font-size: 15px;
  color: var(--sent-text);
}
.history-dlg__title span {
  font-size: 12px;
  color: var(--sent-muted);
}
.history-dlg__close {
  margin-left: auto;
  border: none;
  background: transparent;
  font-size: 20px;
  line-height: 1;
  color: var(--sent-muted);
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 6px;
}
.history-dlg__close:hover {
  color: var(--sent-primary);
  background: var(--sent-primary-soft);
}

.history-dlg__body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 232px 1fr;
}
.history-dlg__list {
  border-right: 1px solid #f2f4f5;
  overflow-y: auto;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #fbfcfc;
}
.history-dlg__row {
  display: flex;
  flex-direction: column;
  gap: 3px;
  width: 100%;
  text-align: left;
  padding: 9px 10px;
  border: 1px solid #edf0f2;
  border-radius: 9px;
  background: #fff;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
}
.history-dlg__row:hover {
  border-color: var(--sent-primary);
}
.history-dlg__row.is-active {
  border-color: var(--sent-primary);
  background: var(--sent-primary-soft);
}
.history-dlg__row-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.history-dlg__row-top b {
  font-size: 12px;
  color: var(--sent-text);
}
.history-dlg__tag {
  font-style: normal;
  font-size: 10px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 999px;
  flex: none;
}
.history-dlg__tag--active {
  background: #e7f1ec;
  color: var(--sent-primary);
}
.history-dlg__tag--waiting {
  background: #fff7e6;
  color: #b8741a;
}
.history-dlg__tag--closed {
  background: #f2f4f5;
  color: var(--sent-muted);
}
.history-dlg__row-time {
  font-size: 11px;
  color: var(--sent-muted);
}
.history-dlg__row-staff {
  font-size: 11px;
  color: #9aa3af;
}

.history-dlg__messages {
  overflow-y: auto;
  padding: 14px 16px;
  background: linear-gradient(180deg, #fffaf6 0%, #ffffff 60%);
}
.history-dlg__msg-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.history-dlg__msg {
  display: flex;
  flex-direction: column;
  gap: 3px;
  max-width: 88%;
}
.history-dlg__msg--self {
  align-self: flex-end;
  align-items: flex-end;
}
.history-dlg__msg--other {
  align-self: flex-start;
  align-items: flex-start;
}
.history-dlg__msg-time {
  font-size: 10px;
  color: #a0a6b2;
}
.history-dlg__bubble {
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.55;
  word-break: break-word;
}
.history-dlg__msg--other .history-dlg__bubble {
  background: #fff;
  border: 1px solid #f2e8e1;
  color: var(--sent-text);
}
.history-dlg__msg--self .history-dlg__bubble {
  background: var(--sent-primary);
  color: #fff;
}
.history-dlg__msg-img {
  max-width: 160px;
  max-height: 160px;
  border-radius: 8px;
}
.history-dlg__state {
  padding: 28px 12px;
  text-align: center;
  font-size: 12px;
  color: var(--sent-muted);
}
/* 系统提示 / 评价结果：居中灰字，和左右气泡区分开 */
.history-dlg__system {
  align-self: center;
  max-width: 92%;
  margin: 2px 0;
  text-align: center;
  font-size: 11px;
  line-height: 1.5;
  color: #8a94a6;
}

@media (max-width: 720px) {
  .history-dlg__body {
    grid-template-columns: 1fr;
    grid-template-rows: 190px 1fr;
  }
  .history-dlg__list {
    border-right: none;
    border-bottom: 1px solid #f2f4f5;
  }
}
</style>
